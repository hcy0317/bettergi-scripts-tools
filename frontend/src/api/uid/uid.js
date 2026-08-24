import service from '@utils/request.js'
import {ElMessage} from "element-plus";

/**
 * 查询全部 uid 映射
 */
export async function getAllUid() {
    const {data} = await service.get('/jwt/uid/selection/all')
    return data;
}

export async function getUidMappings() {
    const {data} = await service.get('/jwt/uid/all')
    return data
}

export async function setDefaultUid(uid) {
    const {code, data} = await service.put('/jwt/uid/default', null, {params: {uid}})
    if (code === 200) ElMessage.success('默认 UID 已更新')
    return data
}

/**
 * 查询 uid 映射
 * @param {string} uid
 */
export async function getUid(uid) {
    const {code,data} = await service.get('/jwt/uid/info', {params: {uid: uid}})
    if (code === 200) {
        ElMessage.success("加载成功");
    }
    return data;
}

/**
 * 新增 uid 映射
 * @param {Object} uidInfo
 */
export async function saveUid(uidInfo) {
    const {code,data} = await service.post('/jwt/uid/info', uidInfo)
    if (code === 200) {
        ElMessage.success("保存成功");
    }
    return data;
}

/**
 * 移除 uid 映射
 * @param {string|array} ids - uid 字符串或数组
 */
export async function removeUidList(ids) {
    const idStr = Array.isArray(ids) ? ids.join(',') : ids
    const {code,data} = service.delete('/jwt/uid/info', {
        params: {ids: idStr}
    })
    if (code === 200) {
        ElMessage.success("移除成功");
    }
    return data;
}
