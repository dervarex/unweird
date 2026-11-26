package com.dervarex.examples.chart;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Title("Chart")
public class ChartScreen extends Screen {

    private final Random random = new Random(7);
    @State List<BarChart.Bar> week = randomWeek();

    Text heading   = new Text("Visitors this week");
    Text subtitle  = new Text(() -> week.stream().mapToLong(b -> Math.round(b.value())).sum() + " in total");
    BarChart chart = new BarChart(() -> week);
    Button shuffle = new Button("Shuffle").onClick(() -> week = randomWeek());

    private List<BarChart.Bar> randomWeek() {
        return Stream.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .map(day -> new BarChart.Bar(day, 20 + random.nextInt(120)))
                .toList();
    }
}
