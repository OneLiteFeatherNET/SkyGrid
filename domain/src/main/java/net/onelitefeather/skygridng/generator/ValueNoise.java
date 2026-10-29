package net.onelitefeather.skygridng.generator;


/**
 * Deterministic 3D value noise in [0,1), seed based and dependency free.
 *
 * Corner values come from a hashed lattice (so they are stable per integer coordinate), then are
 * trilinearly interpolated with a smoothstep fade. Nearby points get similar values, which the
 * noise clustered SkyGrid uses to group similar blocks.
 */
public final class ValueNoise {

    private final long seed;

    public ValueNoise(long seed) {
        this.seed = seed;
    }

    public double sample(double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;

        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);

        double c000 = corner(x0, y0, z0);
        double c100 = corner(x0 + 1, y0, z0);
        double c010 = corner(x0, y0 + 1, z0);
        double c110 = corner(x0 + 1, y0 + 1, z0);
        double c001 = corner(x0, y0, z0 + 1);
        double c101 = corner(x0 + 1, y0, z0 + 1);
        double c011 = corner(x0, y0 + 1, z0 + 1);
        double c111 = corner(x0 + 1, y0 + 1, z0 + 1);

        double x00 = lerp(c000, c100, u);
        double x10 = lerp(c010, c110, u);
        double x01 = lerp(c001, c101, u);
        double x11 = lerp(c011, c111, u);

        double y0i = lerp(x00, x10, v);
        double y1i = lerp(x01, x11, v);

        return lerp(y0i, y1i, w);
    }

    private double corner(int x, int y, int z) {
        return Seeds.unitDouble(Seeds.mix3(seed, x, y, z));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}
