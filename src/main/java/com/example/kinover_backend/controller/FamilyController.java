package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.FamilyDTO;
import com.example.kinover_backend.dto.UserStatusDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.service.FamilyService;
import com.example.kinover_backend.service.UserFamilyService;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "가족 Controller", description = "가족 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;
    private final UserService userService;
    private final UserFamilyService userFamilyService;

    @Autowired
    public FamilyController(
            FamilyService familyService,
            UserService userService,
            UserFamilyService userFamilyService
    ) {
        this.familyService = familyService;
        this.userService = userService;
        this.userFamilyService = userFamilyService;
    }

    /**
     * ✅ 필터(JwtAuthenticationFilter)가 넣어준 userId를 그대로 꺼내 쓰는 방식
     * - Authorization 헤더 직접 파싱 제거(불일치/중복 Bearer/공백 이슈 방지)
     */
    private Long getAuthUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        return (Long) auth.getPrincipal();
    }

    @Operation(summary = "가족 아이디로 가족 조회", description = "특정 가족의 정보를 조회합니다.")
    @PostMapping("/{familyId}")
    public FamilyDTO getFamily(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId
    ) {
        return familyService.getFamilyById(familyId);
    }

    @Operation(summary = "가족 추가", description = "입력값 없이 호출하면 새로운 가족 그룹을 생성하고 ID를 반환합니다.")
    @PostMapping("/add")
    public FamilyDTO addFamily() {
        return familyService.createFamily();
    }

    // ✅ 내 토큰 유저를 특정 가족에 참여시키기
    @Operation(summary = "가족 참여(본인)", description = "토큰의 유저를 해당 가족에 추가합니다.")
    @PostMapping("/join/{familyId}")
    public ResponseEntity<String> joinFamily(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId
    ) {
        Long authenticatedUserId = getAuthUserId();
        userFamilyService.addUserByFamilyIdAndUserId(familyId, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body("가족 구성원이 성공적으로 추가되었습니다");
    }

    // ✅ 가족 생성 + 내가 그 가족에 참여까지 한 번에
    @Operation(summary = "가족 생성 후 바로 참여(본인)", description = "새 가족 생성 후 토큰 유저를 자동으로 추가합니다.")
    @PostMapping("/create-and-join")
    public ResponseEntity<FamilyDTO> createAndJoin() {
        Long authenticatedUserId = getAuthUserId();

        // 1) 가족 생성
        FamilyDTO created = familyService.createFamily();

        // 2) 생성된 가족에 나 추가
        userFamilyService.addUserByFamilyIdAndUserId(created.getFamilyId(), authenticatedUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "가족 삭제", description = "특정 가족을 삭제합니다.")
    @PostMapping("/delete/{familyId}")
    public void deleteFamily(
            @Parameter(description = "삭제할 가족의 아이디", required = true) @PathVariable UUID familyId
    ) {
        familyService.deleteFamily(familyId);
    }

    @Operation(summary = "가족 정보 수정", description = "가족 정보를 수정합니다.")
    @PostMapping("/modify")
    public FamilyDTO modifyFamily(
            @RequestBody Family family
    ) {
        return familyService.modifyFamily(family);
    }

    // (레거시) familyId/userId로 추가(본인만 가능)
    @Operation(summary = "가족 구성원 추가(레거시)", description = "레거시: familyId/userId로 추가합니다.")
    @PostMapping("/add/{familyId}/{userId}")
    public ResponseEntity<String> addFamilyUserLegacy(
            @PathVariable UUID familyId,
            @PathVariable Long userId
    ) {
        Long authenticatedUserId = getAuthUserId();

        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("자신만 가족에 추가할 수 있습니다");
        }

        userFamilyService.addUserByFamilyIdAndUserId(familyId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body("가족 구성원이 성공적으로 추가되었습니다");
    }

    @Operation(summary = "가족 구성원 탈퇴", description = "가족에서 탈퇴합니다.")
    @PostMapping("/delete-user/{familyId}/{userId}")
    public void deleteFamilyUser(
            @PathVariable UUID familyId,
            @PathVariable Long userId
    ) {
        userFamilyService.deleteUserByFamilyIdAndUserId(familyId, userId);
    }

    @Operation(summary = "가족 접속 상태 조회", description = "familyId에 해당하는 가족 구성원의 현재 접속 상태 및 마지막 접속 시간을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "접속 상태 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserStatusDTO.class))))
    @GetMapping("/family-status")
    public ResponseEntity<List<UserStatusDTO>> getFamilyStatus(
            @RequestParam UUID familyId
    ) {
        List<UserStatusDTO> statusList = userService.getFamilyStatus(familyId);
        return ResponseEntity.ok(statusList);
    }

    @Operation(summary = "가족 공지사항 조회")
    @GetMapping("/notice/{familyId}")
    public ResponseEntity<String> getFamilyNotice(@PathVariable UUID familyId) {
        return ResponseEntity.ok(familyService.getNotice(familyId));
    }

    @Operation(summary = "가족 공지사항 수정")
    @PutMapping("/notice/{familyId}")
    public ResponseEntity<Void> updateFamilyNotice(
            @PathVariable UUID familyId,
            @RequestBody String content
    ) {
        familyService.updateNotice(familyId, content);
        return ResponseEntity.ok().build();
    }
}
