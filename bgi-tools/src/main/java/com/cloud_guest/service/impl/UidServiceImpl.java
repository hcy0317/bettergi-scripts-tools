package com.cloud_guest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloud_guest.entitys.domain.UidInfo;
import com.cloud_guest.mapper.UidMapper;
import com.cloud_guest.entitys.pojo.UidInfoConfig;
import com.cloud_guest.service.UidService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author yan
 * @Date 2026/3/30 17:39:54
 * @Description
 */
@Service
public class UidServiceImpl extends ServiceImpl<UidMapper, UidInfoConfig> implements UidService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeList(List<String> ids) {
        boolean removed = removeByIds(ids);
        if (removed && count(Wrappers.lambdaQuery(UidInfoConfig.class)
                .eq(UidInfoConfig::getDefaultUid, Boolean.TRUE)) == 0) {
            findUidAll().stream().findFirst().ifPresent(config -> setDefault(config.getUid()));
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveInfo(UidInfo uidInfo) {
        UidInfoConfig existing = getById(uidInfo.getUid());
        UidInfoConfig config = uidInfo.toConfig();
        if (existing != null && (uidInfo.getPassword() == null || uidInfo.getPassword().isBlank())) {
            config.setPassword(existing.getPassword());
            config.setSalt(existing.getSalt());
        }
        if (existing != null && uidInfo.getDefaultUid() == null) {
            config.setDefaultUid(existing.getDefaultUid());
        }

        boolean hasDefault = count(Wrappers.lambdaQuery(UidInfoConfig.class)
                .eq(UidInfoConfig::getDefaultUid, Boolean.TRUE)) > 0;
        boolean makeDefault = Boolean.TRUE.equals(uidInfo.getDefaultUid()) || !hasDefault;
        if (makeDefault) {
            clearDefault();
            config.setDefaultUid(Boolean.TRUE);
        } else if (config.getDefaultUid() == null) {
            config.setDefaultUid(Boolean.FALSE);
        }
        return saveOrUpdate(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(String uid) {
        if (getById(uid) == null) {
            UidInfoConfig config = new UidInfoConfig(uid, "未命名账号", null, null);
            config.setDefaultUid(Boolean.FALSE);
            save(config);
        }
        clearDefault();
        return update(Wrappers.lambdaUpdate(UidInfoConfig.class)
                .eq(UidInfoConfig::getUid, uid)
                .set(UidInfoConfig::getDefaultUid, Boolean.TRUE));
    }

    private void clearDefault() {
        update(Wrappers.lambdaUpdate(UidInfoConfig.class)
                .eq(UidInfoConfig::getDefaultUid, Boolean.TRUE)
                .set(UidInfoConfig::getDefaultUid, Boolean.FALSE));
    }

    @Override
    public List<UidInfoConfig> findUidAll() {
        return list(Wrappers.lambdaQuery(UidInfoConfig.class)
                .orderByDesc(UidInfoConfig::getDefaultUid)
                .orderByAsc(UidInfoConfig::getUid));
    }

    @Override
    public UidInfoConfig find(String uid) {
        UidInfoConfig uidInfo = getById(uid);
        return uidInfo;
    }
}
