package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.*;
import com.example.ioedunew.repository.*;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Durable results with bounded local workers. Submission row lock coalesces concurrent starts. */
@Service
public class ReviewJobService {
    private final SubmissionRepository submissions;
    private final ProjectRepository projects;
    private final ReviewJobRepository jobs;
    private final SubmissionMaterialService materials;
    private final ReviewModelService models;
    private final AiReviewService reviews;
    private final AiConfigService configs;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16), r -> {
        Thread t=new Thread(r,"submission-review");t.setDaemon(true);return t;
    });
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"review-heartbeat");t.setDaemon(true);return t;
    });
    public ReviewJobService(SubmissionRepository submissions,ProjectRepository projects,ReviewJobRepository jobs,
            SubmissionMaterialService materials,ReviewModelService models,AiReviewService reviews,AiConfigService configs,
            ObjectMapper json,PlatformTransactionManager manager) {
        this.submissions=submissions;this.projects=projects;this.jobs=jobs;this.materials=materials;this.models=models;
        this.reviews=reviews;this.configs=configs;this.json=json;this.tx=new TransactionTemplate(manager);
        heartbeat.scheduleWithFixedDelay(this::touchActive,20,20,TimeUnit.SECONDS);
    }
    public Map<String,Object> start(Long submissionId,boolean force,String retryAttachment) {
        String tenant=TenantContext.require();
        final boolean[] created={false};
        ReviewJob job=tx.execute(status -> {
            Submission submission=submissions.lockById(submissionId).orElseThrow(() -> new BusinessException(404,"提交不存在"));
            Project project=projects.findById(submission.getProjectId()).orElse(null);
            ReviewJob previous=jobs.findTopBySubmissionIdOrderByIdDesc(submissionId).orElse(null);
            String fingerprint=fingerprint(submission,project);
            if(previous!=null && running(previous) && !expired(previous)) return previous;
            if(previous!=null && "DONE".equals(previous.getStatus()) && fingerprint.equals(previous.getFingerprint()) && !force && retryAttachment==null) return previous;
            AiConfigService.AiConfig ai = configs.effective();
            if(ai == null || !ai.isReady() || ai.baseUrl == null || ai.baseUrl.trim().isEmpty()
                    || ai.model == null || ai.model.trim().isEmpty())
                throw new BusinessException("请先在AI设置中配置并启用评分模型");
            ArrayNode files=materials.list(submission);
            if(retryAttachment!=null) {
                boolean found=false;for(JsonNode file:files) if(retryAttachment.equals(file.path("url").asText())) found=true;
                if(!found) throw new BusinessException("附件不属于该成果");
            }
            ReviewJob next=new ReviewJob();next.setSubmissionId(submissionId);next.setFingerprint(fingerprint);
            next.setStatus("QUEUED");next.setMessage("等待评审");next.setMaterials(previous==null ? "[]" : previous.getMaterials());
            created[0]=true;return jobs.saveAndFlush(next);
        });
        if(created[0]) {
            String activeKey=tenant+":"+job.getId();
            activeTasks.put(activeKey,job.getId());
            // Include tenant in ownership key: different databases reuse numeric IDs.
            try { workers.execute(() -> TenantContext.runAs(tenant,() -> execute(job.getId(),retryAttachment))); }
            catch(RejectedExecutionException e) {activeTasks.remove(activeKey);update(job.getId(),"FAILED",0,"评审队列已满，请稍后重试",null,null);}
        }
        return status(submissionId);
    }
    public Map<String,Object> status(Long submissionId) {
        if(!submissions.existsById(submissionId)) throw new BusinessException(404,"提交不存在");
        ReviewJob job=jobs.findTopBySubmissionIdOrderByIdDesc(submissionId).orElse(null);
        Map<String,Object> response=new LinkedHashMap<>();
        if(job==null) {response.put("status","IDLE");return response;}
        response.put("id",job.getId());response.put("status",running(job)&&expired(job)?"FAILED":job.getStatus());
        response.put("message",running(job)&&expired(job)?"任务已中断，请重试（已解析材料可复用）":job.getMessage());
        response.put("progress",job.getProgress());response.put("updatedAt",job.getUpdatedAt());
        try {response.put("materials",json.readTree(job.getMaterials()==null?"[]":job.getMaterials()));
            if(job.getResult()!=null) response.put("result",json.readTree(job.getResult()));
        } catch(Exception e) {throw new BusinessException("评审记录读取失败");}
        Submission submission=submissions.findById(submissionId).get();
        response.put("stale",!job.getFingerprint().equals(fingerprint(submission,projects.findById(submission.getProjectId()).orElse(null))));
        return response;
    }
    private final ConcurrentMap<String,Long> activeTasks=new ConcurrentHashMap<>();
    private void execute(Long id,String retryAttachment) {
        String tenant=TenantContext.require(), key=tenant+":"+id;
        activeTasks.put(key,id);
        ArrayNode done=json.createArrayNode();
        try {
            ReviewJob job=jobs.findById(id).get();
            Submission submission=submissions.findById(job.getSubmissionId()).orElseThrow(() -> new BusinessException("成果已删除"));
            Project project=projects.findById(submission.getProjectId()).orElse(null);
            if(!job.getFingerprint().equals(fingerprint(submission,project))) throw new BusinessException("评分标准或模型配置已变更，请重新评审");
            ArrayNode files=materials.list(submission);
            JsonNode cache=json.readTree(job.getMaterials()==null?"[]":job.getMaterials());
            for(int i=0;i<files.size();i++) {
                JsonNode existing=null;
                for(JsonNode cached:cache) if(files.get(i).path("url").asText().equals(cached.path("url").asText())) existing=cached;
                done.add(existing==null ? json.createObjectNode() : existing);
            }
            update(id,"PARSING",5,"正在解析附件",done.toString(),null);
            for(int i=0;i<files.size();i++) {
                if(!job.getFingerprint().equals(fingerprint(submission,projects.findById(submission.getProjectId()).orElse(null))))
                    throw new BusinessException("评分标准或模型配置已变更，请重新评审");
                JsonNode file=files.get(i);JsonNode prior=null;
                for(JsonNode cached:cache) if(file.path("url").asText().equals(cached.path("url").asText())
                        && materials.fingerprint().equals(cached.path("fingerprint").asText())) prior=cached;
                boolean reuse=prior!=null && !file.path("url").asText().equals(retryAttachment)
                        && ("DONE".equals(prior.path("status").asText()) || "PARTIAL".equals(prior.path("status").asText()) || retryAttachment!=null);
                update(id,"PARSING",5+65*i/Math.max(1,files.size()),"正在解析："+file.path("name").asText(),null,null);
                final int position=i;
                if(reuse)done.set(i,prior);
                else {
                    done.set(i,materials.extract(file,i,prior, current -> {
                        done.set(position,current);
                        update(id,"PARSING",5+65*position/Math.max(1,files.size()),"正在解析："+file.path("name").asText(),done.toString(),null);
                    }));
                }
                update(id,"PARSING",5+65*(i+1)/Math.max(1,files.size()),"已处理"+(i+1)+"/"+files.size()+"个附件",done.toString(),null);
            }
            update(id,"REVIEWING",80,"正在按评分细则生成建议",done.toString(),null);
            if(!job.getFingerprint().equals(fingerprint(submission,projects.findById(submission.getProjectId()).orElse(null))))
                throw new BusinessException("评分标准或模型配置已变更，请重新评审");
            Map<String,Object> result=reviews.evaluate(submission,project,done);
            update(id,"DONE",100,"评审完成，请教师核对",done.toString(),json.writeValueAsString(result));
        } catch(Exception e) {
            String message=(e instanceof BusinessException || e instanceof AiClient.AiUnavailableException)
                    ? e.getMessage() : "评审未完成，请检查AI配置后重试";
            update(id,"FAILED",0,message,done.toString(),null);
        } finally {activeTasks.remove(key);}
    }
    private void update(Long id,String state,int progress,String message,String material,String result) {
        ReviewJob snapshot=jobs.findById(id).orElse(null); if(snapshot==null)return;
        tx.execute(status -> {
            submissions.lockById(snapshot.getSubmissionId());
            ReviewJob job=jobs.findById(id).orElse(null);if(job==null)return null;
            // Only the newest job may publish results after an interrupted job was replaced.
            if(!jobs.findTopBySubmissionIdOrderByIdDesc(job.getSubmissionId()).map(x->x.getId().equals(id)).orElse(false)) throw new BusinessException("任务已被新评审替代");
            job.setStatus(state);job.setProgress(progress);job.setMessage(message);job.setUpdatedAt(LocalDateTime.now());
            if(material!=null)job.setMaterials(material);if(result!=null)job.setResult(result);jobs.save(job);return null;
        });
    }
    private void touchActive() {
        activeTasks.forEach((key,id) -> {
            String tenant=key.substring(0,key.lastIndexOf(':'));
            try {TenantContext.runAs(tenant,() -> {
                ReviewJob snapshot=jobs.findById(id).orElse(null);
                if(snapshot==null)return;
                tx.execute(status -> {
                    submissions.lockById(snapshot.getSubmissionId());
                    ReviewJob job=jobs.findById(id).orElse(null);
                    if(job!=null && running(job)){job.setUpdatedAt(LocalDateTime.now());jobs.save(job);}
                    return null;
                });
            });} catch(Exception ignored) { /* transient DB errors are retried by the next heartbeat */ }
        });
    }
    private String fingerprint(Submission s,Project p) {
        AiConfigService.AiConfig cfg=configs.effective();
        String data="review-v3|"+s.getContent()+"|"+s.getAttachments()+"|"+s.getAttachmentUrl()+"|"+s.getAssessmentName()
            +"|"+(p==null?"":p.getReviewRubric()+p.getAssessments()+p.getLearningGoals()+p.getSyllabus()+p.getSkillRequirements()+p.getSubmissionRequirements()+p.getReferenceAnswer())
            +"|"+cfg.baseUrl+"|"+cfg.model+"|"+cfg.apiKey+"|"+cfg.enabled+"|"+materials.fingerprint();
        return org.springframework.util.DigestUtils.md5DigestAsHex(data.getBytes(StandardCharsets.UTF_8));
    }
    private boolean running(ReviewJob job) {return Arrays.asList("QUEUED","PARSING","REVIEWING").contains(job.getStatus());}
    private boolean expired(ReviewJob job) {return job.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(15));}
    @PreDestroy public void shutdown() {workers.shutdownNow();heartbeat.shutdownNow();}
}
