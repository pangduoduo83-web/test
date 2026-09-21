import axios from 'axios'
import http from './http'

// A separate client avoids sending the application's Bearer token to OSS.
const objectClient = axios.create({ timeout: 15 * 60 * 1000, withCredentials: false })

export async function uploadAsset(file, kind, onUploadProgress) {
  const grant = await http.post('/upload/direct/initiate', { kind, name: file.name, size: file.size })
  if (grant.mode === 'server') {
    const body = new FormData()
    body.append('file', file)
    const suffix = kind === 'image' ? '' : `/${kind}`
    return http.post(`/upload${suffix}`, body, { timeout: 15 * 60 * 1000, onUploadProgress })
  }
  if (grant.mode !== 'oss' || !/^https:\/\/[a-z0-9-]+\.oss-[a-z0-9-]+\.aliyuncs\.com$/.test(grant.host)) {
    throw new Error('上传配置无效，请联系管理员')
  }
  const body = new FormData()
  Object.entries(grant.fields).forEach(([name, value]) => body.append(name, value))
  body.append('file', file) // OSS requires file to be the last form field.
  try {
    await objectClient.post(grant.host, body, { onUploadProgress })
  } catch (error) {
    // A response can be lost after OSS accepted the file; verification is safe and idempotent.
    try { return await http.post(`/upload/direct/${grant.id}/complete`, null, { timeout: 60000 }) }
    catch { throw new Error('文件直传未完成，请检查网络后重试') }
  }
  // Retry confirmation, never resend a large file solely because a small confirmation response was lost.
  let failure
  for (let attempt = 0; attempt < 3; attempt++) {
    try { return await http.post(`/upload/direct/${grant.id}/complete`, null, { timeout: 60000 }) }
    catch (error) {
      failure = error
      const status = error.status || error.response?.status
      if (status && status < 500) throw error
      if (attempt < 2) await new Promise(resolve => setTimeout(resolve, (attempt + 1) * 1000))
    }
  }
  throw failure
}
