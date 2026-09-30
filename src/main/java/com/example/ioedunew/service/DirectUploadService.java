package com.example.ioedunew.service;

import com.aliyun.oss.model.ObjectMetadata;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.*;
import com.example.ioedunew.repository.*;
import com.example.ioedunew.tenant.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DirectUploadService {
    private final StorageProperties config;
    private final UploadStorage storage;
    private final OssObjectStore oss;
    private final OssUploadPolicy policies;
    private final DirectUploadRepository uploads;
    private final StoredFileRepository files;
    private final SubmissionAssetRepository assets;
    private final TenantQuotaService quota;
    private final StorageQuotaLock lock;

    public DirectUploadService(StorageProperties config, UploadStorage storage, OssObjectStore oss, OssUploadPolicy policies,
            DirectUploadRepository uploads, StoredFileRepository files, SubmissionAssetRepository assets,
            TenantQuotaService quota, StorageQuotaLock lock) {
        this.config=config; this.storage=storage; this.oss=oss; this.policies=policies; this.uploads=uploads;
        this.files=files; this.assets=assets; this.quota=quota; this.lock=lock;
    }

    public Map<String, Object> initiate(AuthUser user, String kind, String name, long size) {
        String ext = validate(user, kind, name, size);
        if (!oss.enabled() || !config.getOss().isDirectUpload()) return Collections.singletonMap("mode", "server");
        int seconds=config.getOss().getUploadSeconds();
        if (seconds < 60 || seconds > 1800) throw new BusinessException(503, "直传凭证有效期须为60至1800秒");
        String host=policies.host();
        return lock.run(() -> {
            LocalDateTime now=LocalDateTime.now();
            uploads.deleteByExpiresAtBefore(now.minusDays(2));
            if (uploads.countByUserIdAndCompletedFalseAndExpiresAtAfter(user.getId(), now) >= 10)
                throw new BusinessException(429, "待完成上传过多，请完成已有上传或稍后重试");
            quota.checkStorageQuota(size);
            String id=UUID.randomUUID().toString().replace("-", "");
            String relative=LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "/" + id + "." + ext;
            DirectUpload grant=new DirectUpload(); grant.setId(id); grant.setUserId(user.getId()); grant.setKind(kind);
            grant.setName(name); grant.setSizeBytes(size); grant.setRelativePath(relative); grant.setBucket(oss.bucket());
            grant.setStagingKey(oss.prefix() + "/staging/" + TenantContext.require() + "/" + relative);
            // Keep the quota reservation longer than the POST grant to allow a slow upload to finish.
            grant.setExpiresAt(now.plusSeconds(seconds + 3600));
            Map<String,Object> result=new LinkedHashMap<>(); result.put("mode", "oss"); result.put("id", id); result.put("host", host);
            result.put("fields", policies.fields(grant.getStagingKey(), size, UploadStorage.contentType(relative), id, Instant.now()));
            result.put("expiresIn", seconds); uploads.saveAndFlush(grant);
            quota.invalidateStorage(TenantContext.require()); return result;
        });
    }

    public Map<String, String> complete(AuthUser user, String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new BusinessException("上传编号无效");
        return lock.run(() -> {
            DirectUpload grant=uploads.findByIdAndUserId(id,user.getId()).orElseThrow(() -> new BusinessException(404,"上传记录不存在"));
            String relative=grant.getRelativePath(), key=storage.objectKey(relative);
            if (!oss.bucket().equals(grant.getBucket()) || !grant.getStagingKey().equals(oss.prefix()+"/staging/"+TenantContext.require()+"/"+relative))
                throw new BusinessException(503,"上传存储配置已变化，请重新上传");
            if (grant.isCompleted()) return result(grant);
            if (grant.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException(410,"上传确认已过期，请重新上传");
            validate(user, grant.getKind(), grant.getName(), grant.getSizeBytes());
            ObjectMetadata meta=oss.metadata(grant.getStagingKey());
            if (meta == null) throw new BusinessException(409,"文件尚未上传完成，请稍后重试确认");
            Map<String, String> userMetadata = meta.getUserMetadata();
            String uploadId = userMetadata == null ? null : userMetadata.get("upload-id");
            if (meta.getContentLength()!=grant.getSizeBytes() || !id.equals(uploadId))
                throw new BusinessException(409,"上传文件校验失败，请重新上传");
            // Copy to an unexposed final key: even replaying a valid POST cannot overwrite an accepted attachment.
            oss.promote(grant.getStagingKey(), key, meta.getETag(), UploadStorage.contentType(relative));
            ObjectMetadata finalMeta=oss.metadata(key);
            if (finalMeta==null || finalMeta.getContentLength()!=grant.getSizeBytes())
                throw new BusinessException(502,"文件确认未完成，请稍后重试确认");
            StoredFile file=new StoredFile(); file.setRelativePath(relative); file.setObjectKey(key);
            file.setBucket(oss.bucket()); file.setSizeBytes(grant.getSizeBytes()); files.saveAndFlush(file);
            if ("submission".equals(grant.getKind())) {
                SubmissionAsset asset=new SubmissionAsset(); asset.setUserId(user.getId()); asset.setName(grant.getName());
                asset.setUrl("/uploads/"+relative); asset.setSize(grant.getSizeBytes()); assets.saveAndFlush(asset);
            }
            grant.setCompleted(true); uploads.saveAndFlush(grant); quota.invalidateStorage(TenantContext.require());
            // Staged copies are removed by the dedicated OSS lifecycle rule, after the grant has expired.
            return result(grant);
        });
    }

    private Map<String,String> result(DirectUpload grant) {
        Map<String,String> result=new LinkedHashMap<>(); result.put("url","/uploads/"+grant.getRelativePath()); result.put("name",grant.getName()); return result;
    }

    static String validate(AuthUser user, String kind, String name, long size) {
        List<String> allowed;
        if ("image".equals(kind)) allowed=Arrays.asList("png","jpg","jpeg","gif","webp","svg");
        else if ("file".equals(kind)) {
            if (!user.isTeacher() && !user.isAdmin()) throw new BusinessException(403,"仅教师或管理员可上传教学资料");
            allowed=Arrays.asList("png","jpg","jpeg","gif","webp","svg","pdf","doc","docx","ppt","pptx","xls","xlsx","txt","csv","md","zip","rar","7z","mp4","mp3","mov","webm","mkv");
        } else if ("submission".equals(kind)) allowed=Arrays.asList(
                "png","jpg","jpeg","webp","gif","pdf","doc","docx","mp4","mov","webm",
                "zip","rar","7z","c","h","cc","cpp","cxx","java","py","js","jsx","ts","tsx",
                "vue","html","css","scss","sql","json","xml","yaml","yml","md","txt","csv","log",
                "sh","bat","ps1","ino","kicad_sch","kicad_pcb","sch","brd","hex","bin");
        else throw new BusinessException("上传类型无效");
        if (name==null || name.isEmpty() || name.length()>255 || name.indexOf('\0')>=0) throw new BusinessException("文件名须为1至255个字符");
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) throw new BusinessException("不支持的文件类型");
        int mb = "file".equals(kind) ? 500 : ("submission".equals(kind) ? 100 : 30);
        if (size<=0 || size>mb*1024L*1024) throw new BusinessException("文件须为1字节至"+mb+"MB");
        return ext;
    }
}
