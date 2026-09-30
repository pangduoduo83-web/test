// Keep identifiers as strings: never coerce school numbers to Number.
export const USER_IMPORT_HEADERS = ['姓名', '邮箱', '初始密码', '角色(STUDENT/TEACHER/ADMIN/LAB_ADMIN)', '学号', '教师工号', '专业', '年级', '手机号']
export const MAX_IMPORT_ROWS = 500
export const MAX_IMPORT_BYTES = 1024 * 1024

export function parseCsv(text) {
  text = text.replace(/^\uFEFF/, '')
  const firstLine = text.split(/\r?\n/, 1)[0]
  const delimiter = firstLine.includes('\t') ? '\t' : (firstLine.includes(',') ? ',' : '，')
  const rows = []
  let cells = [], value = '', quoted = false, closed = false, line = 1, start = 1
  const cell = () => { cells.push(value); value = ''; closed = false }
  const row = () => {
    cell()
    if (cells.some((v) => v.trim())) rows.push({ line: start, cells })
    cells = []
  }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { value += '"'; i++ }
      else if (ch === '"') { quoted = false; closed = true }
      else { value += ch; if (ch === '\n') line++ }
    } else if (ch === delimiter) cell()
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row(); line++; start = line
    } else if (ch === '"' && !value.trim() && !closed) { value = ''; quoted = true }
    else if (ch === '"' || (closed && ch.trim())) throw new Error('第 ' + line + ' 行引号格式错误')
    else if (!closed) value += ch
  }
  if (quoted) throw new Error('第 ' + start + ' 行的引号未闭合')
  if (value || cells.length || closed) row()
  return rows
}

const aliases = {
  name: ['姓名', 'name'],
  email: ['邮箱', '电子邮箱', 'email'],
  password: ['初始密码', '密码', 'password'],
  role: ['角色', 'role'],
  studentNo: ['学号', 'studentno', 'student_no', '学号/工号'],
  teacherNo: ['教师工号', '教师号', '工号', 'teacherno', 'teacher_no'],
  major: ['专业', 'major'],
  grade: ['年级', 'grade'],
  phone: ['手机号', '手机号码', 'phone']
}
const roles = { STUDENT: 'STUDENT', TEACHER: 'TEACHER', ADMIN: 'ADMIN', LAB_ADMIN: 'LAB_ADMIN',
  学生: 'STUDENT', 教师: 'TEACHER', 管理员: 'ADMIN', 实验室管理员: 'LAB_ADMIN' }
const fieldName = (header) => {
  const normalized = header.trim().replace(/[（(].*$/, '').toLowerCase()
  return Object.keys(aliases).find((key) => aliases[key].includes(normalized))
}

export function prepareUserImport(text, existingUsers = []) {
  const records = parseCsv(text)
  if (records.length < 2) throw new Error('文件没有用户数据,请使用下载的 CSV 模板')
  if (records.length - 1 > MAX_IMPORT_ROWS) throw new Error('每次最多导入 ' + MAX_IMPORT_ROWS + ' 人,请拆分文件')
  const headers = records.shift().cells.map(fieldName)
  for (const field of ['name', 'email', 'password', 'role']) {
    if (!headers.includes(field)) throw new Error('缺少必需的表头：' + aliases[field][0])
  }
  const knownHeaders = headers.filter(Boolean)
  if (new Set(knownHeaders).size !== knownHeaders.length) throw new Error('表头有重复字段,请使用导入模板')
  if (headers.some((h) => !h)) throw new Error('存在无法识别的表头,请使用导入模板并删除多余列')
  const used = new Map()
  for (const user of existingUsers) {
    for (const value of [user.email, user.phone, user.studentNo, user.teacherNo]) {
      if (value?.trim()) used.set(value.trim().toLowerCase(), '现有账号')
    }
  }
  return records.map(({ line, cells }) => {
    const data = {}
    const errors = []
    if (cells.length !== headers.length) errors.push('列数与表头不一致')
    headers.forEach((key, i) => { if (key) data[key] = key === 'password' ? (cells[i] || '') : (cells[i] || '').trim() })
    data.email = data.email.toLowerCase()
    data.role = roles[data.role.toUpperCase()]
    if (!data.role) errors.push('角色无效,请填写 STUDENT、TEACHER、ADMIN 或 LAB_ADMIN')
    // Legacy seven-column template stored teacher numbers in its student-number column.
    if (data.role === 'TEACHER' && !headers.includes('teacherNo')) {
      data.teacherNo = data.studentNo
      data.studentNo = ''
    }
    if (!data.name) errors.push('姓名不能为空')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) errors.push('邮箱格式不正确')
    const bytes = new TextEncoder().encode(data.password).length
    if (!data.password.trim() || data.password.length < 6 || bytes > 72) errors.push('密码至少 6 位,最多 72 个 UTF-8 字节')
    if (data.phone && !/^1\d{10}$/.test(data.phone)) errors.push('手机号需为 11 位有效号码')
    for (const [key, max] of Object.entries({ name: 50, email: 100, studentNo: 30, teacherNo: 30, major: 50, grade: 20 })) {
      if ((data[key] || '').length > max) errors.push(aliases[key][0] + '最多 ' + max + ' 字符')
    }
    for (const key of ['studentNo', 'teacherNo']) {
      if (data[key] && /[\s@]/.test(data[key])) errors.push(aliases[key][0] + '不能包含空白或 @')
      if (data[key] && /^[-+]?\d+(\.\d+)?[eE][-+]?\d+$/.test(data[key])) errors.push(aliases[key][0] + '疑似科学计数法,请在 Excel 中按文本保存完整编号')
    }
    const ownIdentifiers = new Set([data.email, data.phone, data.studentNo, data.teacherNo].filter(Boolean).map((s) => s.toLowerCase()))
    for (const value of ownIdentifiers) {
      if (used.has(value)) errors.push('登录标识 ' + value + ' 与' + used.get(value) + '重复')
    }
    for (const value of ownIdentifiers) if (!used.has(value)) used.set(value, '第 ' + line + ' 行')
    return { line, data, errors, status: errors.length ? '校验失败' : '待导入', reason: errors.join('；') }
  })
}

export function decodeUserCsv(buffer) {
  const bytes = new Uint8Array(buffer)
  if (bytes.length > MAX_IMPORT_BYTES) throw new Error('文件不能超过 1 MB')
  let encoding = 'utf-8'
  if (bytes[0] === 0xff && bytes[1] === 0xfe) encoding = 'utf-16le'
  if (bytes[0] === 0xfe && bytes[1] === 0xff) encoding = 'utf-16be'
  try { return new TextDecoder(encoding, { fatal: true }).decode(buffer) }
  catch { throw new Error('文件编码无法识别,请在 Excel 中另存为 CSV UTF-8 后重试') }
}
