package com.cloud_guest.entitys.domain;

import com.cloud_guest.entitys.pojo.UidInfoConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/**
 * @Author yan
 * @Date 2026/3/30 17:24:44
 * @Description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UidInfo {
    @Schema(description = "uid")
    @NotBlank(message = "uid不能为空")
    private String uid;
    @NotBlank(message = "as不能为空")
    private String as;
    private String gameNickname;
    private String miliastraNickname;
    private String username;
    private String password;
    private Boolean defaultUid;

    @SneakyThrows
    public UidInfoConfig toConfig(){
        UidInfoConfig uidInfoConfig = new UidInfoConfig(
                uid, as, gameNickname, miliastraNickname, username, password);
        uidInfoConfig.setDefaultUid(defaultUid);
        return uidInfoConfig;
    }
}
