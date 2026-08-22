package com.trading.portfolio;

import com.trading.portfolio.dto.PositionResponse;
import com.trading.signals.Position;
import com.trading.signals.PositionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioDbService db;
    private final PortfolioEngine engine;

    /**
     * GET /api/portfolio/positions
     * Returns all positions for the authenticated user.
     * Optional query param: ?status=ACTIVE (filters by status)
     */
    @GetMapping("/positions")
    public List<PositionResponse> getPositions(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) PositionStatus status) {

        Long userId = resolveUserId(principal);
        List<Position> positions = (status == null)
                ? db.getAllPositionsForUser(userId)
                : db.getPositionsByStatus(userId, status);

        return positions.stream().map(PositionResponse::from).toList();
    }

    /**
     * POST /api/portfolio/positions/{id}/exit
     * Triggers a manual exit for the given position (must be ACTIVE and owned by caller).
     */
    @PostMapping("/positions/{id}/exit")
    public ResponseEntity<PositionResponse> manualExit(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveUserId(principal);

        // Ownership check — only the owning user can exit their position
        List<Position> active = db.getActivePositions();
        Position pos = active.stream()
                .filter(p -> p.getId().equals(id) && p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active position not found or not owned by caller: " + id));

        engine.manualExit(id);

        // Return updated position
        Position updated = db.getAllPositionsForUser(userId).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(PositionResponse.from(updated));
    }

    private Long resolveUserId(UserDetails principal) {
        // The username stored in JWT subject is the user's email; we need the numeric ID.
        // PortfolioDbService exposes a helper via UserConfigRepository.
        return db.getUserIdByEmail(principal.getUsername());
    }
}
