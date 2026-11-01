package kr.server.stardewfishing;

import java.util.Random;

/**
 * 스타듀 밸리 BobberBar의 물리를 옮긴 미니게임 본체. Bukkit에 의존하지 않는다.
 * 스타듀는 60fps라서 마인크래프트 1틱(20tps)마다 3프레임씩 돌려 같은 체감을 낸다.
 * y축은 0이 맨 위, TRACK이 맨 아래.
 */
final class FishingGame {

    enum Motion { MIXED, DART, SMOOTH, SINKER, FLOATER }

    static final float TRACK = 568f;
    private static final float FISH_HEIGHT = 28f;
    private static final float FISH_MAX = TRACK - FISH_HEIGHT;
    private static final int FRAMES_PER_TICK = 3;

    private final float barHeight;
    private final float difficulty;
    private final Motion motion;
    private final Random random;

    private float barPos;
    private float barSpeed;
    private float fishPos;
    private float fishSpeed;
    private float fishTarget = -1f;
    private float drift;
    private float progress = 0.3f;

    FishingGame(float barHeight, float difficulty, Motion motion, Random random) {
        this.barHeight = barHeight;
        this.difficulty = difficulty;
        this.motion = motion;
        this.random = random;
        this.barPos = TRACK - barHeight;
        this.fishPos = FISH_MAX * 0.8f;
    }

    void tick(boolean pressed) {
        for (int i = 0; i < FRAMES_PER_TICK && !isWon() && !isLost(); i++) {
            moveFish();
            moveBar(pressed);
            progress = Math.min(1f, Math.max(0f, progress + (isFishInBar() ? 0.002f : -0.003f)));
        }
    }

    private void moveFish() {
        boolean smooth = motion == Motion.SMOOTH;
        if (random.nextDouble() < difficulty * (smooth ? 20 : 1) / 4000f && (!smooth || fishTarget == -1f)) {
            float percent = Math.min(99f, difficulty + range(10, 45)) / 100f;
            fishTarget = fishPos + range(-fishPos, FISH_MAX - fishPos) * percent;
        }

        if (motion == Motion.FLOATER) {
            drift = Math.max(drift - 0.01f, -1.5f);
        } else if (motion == Motion.SINKER) {
            drift = Math.min(drift + 0.01f, 1.5f);
        }

        if (fishTarget != -1f && Math.abs(fishPos - fishTarget) > 3f) {
            float acceleration = (fishTarget - fishPos) / (range(10, 22) + (100f - Math.min(100f, difficulty)));
            fishSpeed += (acceleration - fishSpeed) / 5f;
        } else if (!smooth && random.nextDouble() < difficulty / 2000f) {
            fishTarget = fishPos + jump(0f);
        } else {
            fishTarget = -1f;
        }

        if (motion == Motion.DART && random.nextDouble() < difficulty / 1000f) {
            fishTarget = fishPos + jump(difficulty * 2f);
        }
        if (fishTarget != -1f) {
            fishTarget = Math.max(0f, Math.min(FISH_MAX, fishTarget));
        }

        fishPos = Math.max(0f, Math.min(FISH_MAX, fishPos + fishSpeed + drift));
    }

    private float jump(float extra) {
        return random.nextBoolean() ? range(-100f - extra, -51f) : range(50f, 101f + extra);
    }

    private void moveBar(boolean pressed) {
        float maxPos = TRACK - barHeight;
        float gravity = pressed ? -0.25f : 0.25f;
        if (pressed && (barPos == 0f || barPos == maxPos)) {
            barSpeed = 0f;
        }
        if (isFishInBar()) {
            gravity *= 0.6f;
        }
        barSpeed += gravity;
        barPos += barSpeed;
        if (barPos > maxPos) {
            barPos = maxPos;
            barSpeed = -barSpeed * 2f / 3f;
        } else if (barPos < 0f) {
            barPos = 0f;
            barSpeed = -barSpeed * 2f / 3f;
        }
    }

    private float range(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    boolean isFishInBar() {
        float center = fishCenter();
        return center >= barPos && center <= barPos + barHeight;
    }

    boolean isWon() {
        return progress >= 1f;
    }

    boolean isLost() {
        return progress <= 0f;
    }

    float fishCenter() {
        return fishPos + FISH_HEIGHT / 2f;
    }

    float barTop() {
        return barPos;
    }

    float barBottom() {
        return barPos + barHeight;
    }

    float progress() {
        return progress;
    }
}
