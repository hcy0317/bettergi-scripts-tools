package com.cloud_guest.controller;

import com.cloud_guest.aop.log.SysLog;
import com.cloud_guest.aop.security.Token;
import com.cloud_guest.entitys.domain.UidInfo;
import com.cloud_guest.entitys.pojo.UidInfoConfig;
import com.cloud_guest.cultivation.persistence.CultivationPlanRevisionMapper;
import com.cloud_guest.result.Result;
import com.cloud_guest.service.UidService;
import com.cloud_guest.service.AutoPlanService;
import com.cloud_guest.service.WsProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @Author yan
 * @Date 2026/3/30 17:22:22
 * @Description
 */
@Slf4j
@Tag(name = "uid映射服务")
@RestController
@RequestMapping(value = {"/uid/", "/api/uid/", "/jwt/uid/"})
public class UidController {
    @Resource
    private UidService uidService;
    @Resource
    private AutoPlanService autoPlanService;
    @Resource
    private WsProxyService wsProxyService;
    @Resource
    private CultivationPlanRevisionMapper cultivationPlanRevisionMapper;

    @SysLog
    @Token
    @Operation(summary = "查询全部uid映射")
    @GetMapping("all")
    public Result<List<UidInfo>> all() {
        List<UidInfo> uidAll = uidService.findUidAll().stream().map(UidInfoConfig::toUidInfo).map(o -> {
            o.setPassword(null);
            return o;
        }).toList();
        return Result.ok(uidAll);
    }

    @SysLog
    @Token
    @Operation(summary = "查询所有被配置引用的uid")
    @GetMapping("selection/all")
    public Result<List<UidInfo>> selectionAll() {
        Map<String, UidInfo> result = new LinkedHashMap<>();
        allUidReferences().forEach(uid -> result.put(uid,
                new UidInfo(uid, "未命名账号", null, null,
                        "MannequinGirl", null, null, Boolean.FALSE)));
        uidService.findUidAll().stream()
                .map(UidInfoConfig::toUidInfo)
                .forEach(info -> {
                    info.setPassword(null);
                    result.put(info.getUid(), info);
                });
        List<UidInfo> sorted = result.values().stream()
                .sorted((left, right) -> {
                    int defaultOrder = Boolean.compare(
                            Boolean.TRUE.equals(right.getDefaultUid()),
                            Boolean.TRUE.equals(left.getDefaultUid()));
                    return defaultOrder != 0 ? defaultOrder : left.getUid().compareTo(right.getUid());
                })
                .toList();
        return Result.ok(sorted);
    }

    private List<String> allUidReferences() {
        return java.util.stream.Stream.of(
                        autoPlanService.findUidAll(),
                        wsProxyService.findUidAll(),
                        cultivationPlanRevisionMapper.findDistinctUids())
                .flatMap(List::stream)
                .filter(uid -> uid != null && !uid.isBlank())
                .distinct()
                .toList();
    }

    @SysLog
    @Token
    @Operation(summary = "查询uid映射")
    @GetMapping("info")
    public Result<UidInfo> getUid(@RequestParam String uid) {
        UidInfo uidInfo = Optional.ofNullable(uidService.find(uid))
                .map(UidInfoConfig::toUidInfo)
                .orElse(null);   // 返回一个空的 UidInfo 对象（确保 UidInfo 有无参构造）
        return Result.ok(uidInfo);
    }

    @SysLog
    @Token
    @Operation(summary = "新增uid映射")
    @PostMapping("info")
    public Result uid(@Validated @RequestBody UidInfo uidInfo) {
        uidService.saveInfo(uidInfo);
        return Result.ok();
    }

    @SysLog
    @Token
    @Operation(summary = "设置默认uid")
    @PutMapping("default")
    public Result setDefault(@NotBlank @RequestParam String uid) {
        uidService.setDefault(uid.trim());
        return Result.ok();
    }

    @SysLog
    @Token
    @Operation(summary = "移除uid映射")
    @DeleteMapping("info")
    public Result remove(@NotBlank @RequestParam String ids) {
        List<String> uidList = Arrays.stream(ids.split(",")).toList();
        uidService.removeList(uidList);
        return Result.ok();
    }
}
