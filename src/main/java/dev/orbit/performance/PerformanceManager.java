package dev.orbit.performance;

public final class PerformanceManager {
    private PerformancePreset preset = PerformancePreset.BALANCED;
    private int starDensity = 60;
    private float renderScale = 1.0F;
    private boolean ambientParticles = true;

    public PerformancePreset getPreset() {
        return preset;
    }

    public void setPreset(PerformancePreset preset) {
        this.preset = preset == null ? PerformancePreset.BALANCED : preset;
    }

    public int getStarDensity() {
        return starDensity;
    }

    public void setStarDensity(int starDensity) {
        this.starDensity = Math.max(0, Math.min(starDensity, 200));
    }

    public float getRenderScale() {
        return renderScale;
    }

    public void setRenderScale(float renderScale) {
        this.renderScale = Math.max(0.25F, Math.min(renderScale, 2.0F));
    }

    public boolean isAmbientParticles() {
        return ambientParticles;
    }

    public void setAmbientParticles(boolean ambientParticles) {
        this.ambientParticles = ambientParticles;
    }
}
