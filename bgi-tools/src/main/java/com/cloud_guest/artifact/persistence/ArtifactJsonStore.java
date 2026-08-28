package com.cloud_guest.artifact.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloud_guest.entitys.pojo.DbKV;
import com.cloud_guest.mapper.DbKVMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ArtifactJsonStore {
    private final DbKVMapper mapper;
    private final ObjectMapper objectMapper;

    public ArtifactJsonStore(DbKVMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public synchronized <T> T put(String type, String key, T value) {
        DbKV entity = findEntity(type, key).orElseGet(DbKV::new);
        entity.setType(type);
        entity.setKeyName(key);
        entity.setValue(write(value));
        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return value;
    }

    public <T> Optional<T> get(String type, String key, Class<T> valueType) {
        return findEntity(type, key).map(entity -> read(entity.getValue(), valueType));
    }

    public <T> Optional<T> getByKeySuffix(String type, String suffix, Class<T> valueType) {
        DbKV entity = mapper.selectOne(Wrappers.lambdaQuery(DbKV.class)
                .eq(DbKV::getType, type)
                .likeLeft(DbKV::getKeyName, suffix)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity).map(value -> read(value.getValue(), valueType));
    }

    public <T> List<T> list(String type, Class<T> valueType) {
        return mapper.selectList(Wrappers.lambdaQuery(DbKV.class)
                        .eq(DbKV::getType, type)
                        .orderByAsc(DbKV::getKeyName))
                .stream()
                .map(entity -> read(entity.getValue(), valueType))
                .toList();
    }

    public <T> List<T> listByKeyPrefix(String type, String prefix, Class<T> valueType) {
        return mapper.selectList(Wrappers.lambdaQuery(DbKV.class)
                        .eq(DbKV::getType, type)
                        .likeRight(DbKV::getKeyName, prefix)
                        .orderByDesc(DbKV::getKeyName))
                .stream()
                .map(entity -> read(entity.getValue(), valueType))
                .toList();
    }

    public <T> List<T> listByKeyPrefixLimited(
            String type,
            String prefix,
            Class<T> valueType,
            int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be in [1, 1000]");
        }
        return mapper.selectList(Wrappers.lambdaQuery(DbKV.class)
                        .eq(DbKV::getType, type)
                        .likeRight(DbKV::getKeyName, prefix)
                        .orderByDesc(DbKV::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(entity -> read(entity.getValue(), valueType))
                .toList();
    }

    public <T> List<T> listByKeyPrefixPage(
            String type,
            String prefix,
            Class<T> valueType,
            int limit,
            int offset) {
        if (limit < 1 || limit > 1000 || offset < 0) {
            throw new IllegalArgumentException("invalid page bounds");
        }
        return mapper.selectList(Wrappers.lambdaQuery(DbKV.class)
                        .eq(DbKV::getType, type)
                        .likeRight(DbKV::getKeyName, prefix)
                        .orderByDesc(DbKV::getId)
                        .last("LIMIT " + limit + " OFFSET " + offset))
                .stream()
                .map(entity -> read(entity.getValue(), valueType))
                .toList();
    }

    public <T> List<T> listLimited(
            String type,
            Class<T> valueType,
            int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be in [1, 1000]");
        }
        return mapper.selectList(Wrappers.lambdaQuery(DbKV.class)
                        .eq(DbKV::getType, type)
                        .orderByDesc(DbKV::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(entity -> read(entity.getValue(), valueType))
                .toList();
    }

    public synchronized boolean delete(String type, String key) {
        return mapper.delete(Wrappers.lambdaQuery(DbKV.class)
                .eq(DbKV::getType, type)
                .eq(DbKV::getKeyName, key)) > 0;
    }

    private Optional<DbKV> findEntity(String type, String key) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.lambdaQuery(DbKV.class)
                .eq(DbKV::getType, type)
                .eq(DbKV::getKeyName, key)));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to serialize artifact data", exception);
        }
    }

    private <T> T read(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to deserialize artifact data", exception);
        }
    }
}
