package com.cloud_guest.cultivation.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CultivationExecutionActionMapper extends BaseMapper<CultivationExecutionActionEntity> {
    @Select("""
            SELECT * FROM cultivation_execution_action
            WHERE uid = #{uid} AND plan_revision = #{revision}
              AND status IN ('LEASED', 'AWAITING_RECONCILE')
            ORDER BY create_time DESC LIMIT 1
            """)
    CultivationExecutionActionEntity findLeased(@Param("uid") String uid,
                                                 @Param("revision") int revision);

    @Select("""
            SELECT * FROM cultivation_execution_action
            WHERE uid = #{uid} AND plan_revision = #{revision}
              AND status = 'COMPLETED' AND observed_owned >= 0
            ORDER BY update_time DESC, create_time DESC
            """)
    List<CultivationExecutionActionEntity> findCompletedObservations(
            @Param("uid") String uid, @Param("revision") int revision);
}
