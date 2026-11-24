package com.dervarex.unweird.game;

/** Scatters {@link Plant @Plant} blocks on dry ground: {@code new Plants(flower, tallGrass)}. */
public class Plants extends Part {

    final Block[] plants;
    double amount = 0.06;

    public Plants(Block... plants) {
        this.plants = plants.clone();
    }

    /** A lot more of them. */
    public Plants dense() {
        amount = 0.2;
        return this;
    }

    @Override
    protected void decorate(int x, int z, int top, Area area) {
        if (plants.length == 0 || !chance(x, z, amount) || top <= terrain.seaLevel) {
            return;
        }
        Block ground = area.get(x, top, z);
        if (ground == null || !ground.opaque() || area.get(x, top + 1, z) != null) {
            return;
        }
        int which = (int) (terrain.noise.random(x, 3, z) * plants.length);
        area.set(x, top + 1, z, plants[Math.min(plants.length - 1, which)]);
    }
}
