package com.dervarex.examples.stopwatch;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Unweird.after(...) runs code on the UI thread later; scheduling it again from itself makes a timer. */
@Title("Stopwatch")
public class StopwatchScreen extends Screen {

    @State long elapsed;            // nanoseconds before the current run
    @State long startedAt = -1;     // System.nanoTime() of the current run, -1 while stopped
    @State final List<String> laps = new ArrayList<>();
    private Runnable cancelTick = () -> { };

    Text time   = new Text(() -> format(total())).heading();
    Text hint   = new Text("S starts and stops, L takes a lap, R resets.");

    Button toggle = new Button(() -> running() ? "Stop" : "Start").onClick(this::toggle);
    Button lap    = new Button("Lap").enabledIf(this::running).onClick(this::lap);
    Button reset  = new Button("Reset").ghost().enabledIf(() -> total() > 0).onClick(this::reset);

    ListView<String> lapList = new ListView<>(() -> laps).empty("No laps yet");

    @Shortcut("S")
    void toggle() {
        if (running()) {
            elapsed = total();
            startedAt = -1;
            cancelTick.run();
        } else {
            startedAt = System.nanoTime();
            tick();
        }
    }

    @Shortcut("L")
    void lap() {
        if (running()) {
            laps.add(0, "Lap " + (laps.size() + 1) + "    " + format(total()));
        }
    }

    @Shortcut("R")
    void reset() {
        cancelTick.run();
        elapsed = 0;
        startedAt = -1;
        laps.clear();
    }

    private void tick() {
        cancelTick = Unweird.after(Duration.ofMillis(31), this::tick); // each run is an event, so the text updates
    }

    @Override
    protected void onHide() {
        cancelTick.run();
    }

    private boolean running() {
        return startedAt >= 0;
    }

    private long total() {
        return elapsed + (running() ? System.nanoTime() - startedAt : 0);
    }

    private static String format(long nanos) {
        long millis = nanos / 1_000_000;
        return String.format("%02d:%02d.%02d", millis / 60_000, millis / 1000 % 60, millis / 10 % 100);
    }
}
