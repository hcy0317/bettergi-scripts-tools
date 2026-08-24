import {computed, ref} from 'vue'
import {getAllUid, setDefaultUid as persistDefaultUid} from '@api/uid/uid.js'

const uidOptions = ref([])
const loadingUidOptions = ref(false)
let loadingPromise = null

export function useUidSelection() {
  const defaultUid = computed(() =>
    uidOptions.value.find(item => item.defaultUid)?.uid || uidOptions.value[0]?.uid || '')

  const loadUidOptions = async (force = false) => {
    if (!force && uidOptions.value.length) return uidOptions.value
    if (loadingPromise) return loadingPromise
    loadingUidOptions.value = true
    loadingPromise = getAllUid()
      .then(items => {
        uidOptions.value = Array.isArray(items) ? items : []
        return uidOptions.value
      })
      .finally(() => {
        loadingUidOptions.value = false
        loadingPromise = null
      })
    return loadingPromise
  }

  const setDefaultUid = async uid => {
    await persistDefaultUid(uid)
    await loadUidOptions(true)
  }

  return {uidOptions, defaultUid, loadingUidOptions, loadUidOptions, setDefaultUid}
}
