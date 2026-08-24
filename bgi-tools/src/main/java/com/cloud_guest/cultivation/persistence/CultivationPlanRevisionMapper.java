package com.cloud_guest.cultivation.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CultivationPlanRevisionMapper extends BaseMapper<CultivationPlanRevisionEntity> {
    @Select("SELECT COALESCE(MAX(revision), 0) FROM cultivation_plan_revision WHERE uid = #{uid}")
    Integer findMaxRevision(@Param("uid") String uid);

    @Select("SELECT * FROM cultivation_plan_revision WHERE uid = #{uid} ORDER BY revision DESC LIMIT 1")
    CultivationPlanRevisionEntity findLatest(@Param("uid") String uid);

    @Select("SELECT DISTINCT uid FROM cultivation_plan_revision ORDER BY uid")
    List<String> findDistinctUids();
}
