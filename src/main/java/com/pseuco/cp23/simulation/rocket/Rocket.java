package com.pseuco.cp23.simulation.rocket;

import com.pseuco.cp23.model.Output;
import com.pseuco.cp23.model.PersonInfo;
import com.pseuco.cp23.model.Query;
import com.pseuco.cp23.model.Rectangle;
import com.pseuco.cp23.model.Scenario;
import com.pseuco.cp23.model.Statistics;
import com.pseuco.cp23.model.TraceEntry;
import com.pseuco.cp23.model.XY;
import com.pseuco.cp23.simulation.common.Person;
import com.pseuco.cp23.simulation.common.Simulation;
import com.pseuco.cp23.validator.InsufficientPaddingException;
import com.pseuco.cp23.validator.Validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Your implementation shall go into this class.
 *
 * <p>
 * This class has to implement the <em>Simulation</em> interface.
 * </p>
 */
public class Rocket implements Simulation {
    List<Patch> patches;

    List<List<Person>> lists_of_people_per_tick;
    Scenario scenario;

    BlockingQueue<Pair> results_queue;

    List<TraceEntry> traceEntries;
    Map<String, List<Statistics>> statistics;


    int population_count;

    /**
     * Constructs a rocket with the given parameters.
     *
     * <p>
     * You must not change the signature of this constructor.
     * </p>
     *
     * <p>
     * Throw an insufficient padding exception if and only if the padding is insufficient.
     * Hint: Depending on the parameters, some amount of padding is required even if one
     * only computes one tick concurrently. The padding is insufficient if the provided
     * padding is below this minimal required padding.
     * </p>
     *
     * @param scenario  The scenario to simulate.
     * @param padding   The padding to be used.
     * @param validator The validator to be called.
     */
    public Rocket(Scenario scenario, int padding, Validator validator) throws InsufficientPaddingException {

        this.scenario = scenario;

        // this queue keeps receiving relevant data from other threads
        // to calculate the traces and statistics
        this.results_queue = new LinkedBlockingQueue<>();

        this.traceEntries = new ArrayList<>();
        this.statistics = new HashMap<>();

        for (String queryKey : this.scenario.getQueries().keySet()) {
            statistics.put(queryKey, new ArrayList<>());
        }


        int k = CalculateK(scenario.getParameters().getInfectionRadius(), scenario.getParameters().getIncubationTime(), padding);
        patches = generatePatches(scenario, padding, k, validator);

        lists_of_people_per_tick = new ArrayList<>();
        for (int i = 0; i <= scenario.getTicks(); i++) {
            lists_of_people_per_tick.add(new ArrayList<>());
        }

    }

    @Override
    public Output getOutput() {

        return new Output(this.scenario, this.traceEntries, this.statistics);
    }


    @Override
    public void run() {
        for (Patch patch : patches) {
            patch.start();
        }

        try {
            handleStatistics();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (Patch patch : patches) {
            try {
                patch.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * In this method this thread will keep on consuming the data sent by the
     * patch threads, it will receive Pairs from other threads that contains
     * a tick and a list of people at that tick from a specific patch
     * and then add the people from that tick to the whole grid population
     * at that tick in this thread, once all threads have sent the
     * people info at the tick that is being calculated then the statistics is
     * created, and we proceed to the next tick
     */

    private void handleStatistics() throws InterruptedException {
        int current_tick = 0;

        // keep receiving data pairs from other threads
        while (current_tick <= scenario.getTicks()) {
            Pair people_per_tick = results_queue.take();
            int tick = people_per_tick.tick();

            // add all the people we just received to the list of the whole population at that tick
            lists_of_people_per_tick.get(tick).addAll(people_per_tick.list_of_people());

            // if the size of the list of people at the current tick we are processing
            // is equal to the whole population number meaning that all threads have
            // sent their persons info for that tick then extend output and proceed to next tick

            if (lists_of_people_per_tick.get(current_tick).size() == population_count) {

                lists_of_people_per_tick.get(current_tick).sort(new Person.PersonIDComparator());

                extendOutput(lists_of_people_per_tick.get(current_tick));
                lists_of_people_per_tick.get(current_tick).clear();
                current_tick++;
            }

        }

    }

    private void extendOutput(List<Person> people) {

        if (scenario.getTrace())
            this.traceEntries.add(new TraceEntry(people.stream()
                    .map(Person::getInfo)
                    .collect(Collectors.toList())));

        this.extendStatistics(people);

    }

    private void extendStatistics(List<Person> people) {

        for (Map.Entry<String, Query> entry : this.scenario.getQueries().entrySet()) {
            final Query query = entry.getValue();
            statistics.get(entry.getKey()).add(new Statistics(
                    people.stream().filter(
                            (Person person) -> person.isSusceptible()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    people.stream().filter(
                            (Person person) -> person.isInfected()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    people.stream().filter(
                            (Person person) -> person.isInfectious()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    people.stream().filter(
                            (Person person) -> person.isRecovered()
                                    && query.getArea().contains(person.getPosition())
                    ).count()
            ));
        }

    }


    /**
     * This method generates the threads we will run concurrently
     **/
    private List<Patch> generatePatches(Scenario scenario, int padding, int k, Validator validator) {
        List<Integer> x = scenario.getPartition().getX();
        List<Integer> y = scenario.getPartition().getY();

        //add the gird edges to the partition lists for smoother calculations
        x.add(0, 0);
        x.add(scenario.getGridSize().getX());
        y.add(0, 0);
        y.add(scenario.getGridSize().getY());

        int id = 0;
        List<Patch> patches = new ArrayList<>();

        // go through two loops to create all patches from the x and y partitions
        for (int i = 1; i < y.size(); i++) {
            for (int j = 1; j < x.size(); j++) {

                // calculate the patch grid rectangle
                final XY top_left = new XY(x.get(j - 1), y.get(i - 1));
                final XY patch_size = new XY(x.get(j) - x.get(j - 1), y.get(i) - y.get(i - 1));
                final Rectangle patch_grid = new Rectangle(top_left, patch_size);

                // calculate the padding grid rectangle
                // calculate the padding top left corner
                final XY padding_top_left = new XY(Math.max(0, top_left.getX() - padding),
                        Math.max(0, top_left.getY() - padding));

                // calculate the padding bottom right corner
                final XY padding_bottom_right = new XY(Math.min(x.get(j) + padding, scenario.getGridSize().getX()),
                        Math.min(y.get(i) + padding, scenario.getGridSize().getY()));

                // calculate the padding size and create the rectangle for the padding grid
                final XY padding_size = padding_bottom_right.sub(padding_top_left);
                final Rectangle padding_grid = new Rectangle(padding_top_left, padding_size);

                //create the patch objects and give it the results queue to send back relevant data for statistics
                patches.add(new Patch(id, this.results_queue, validator, patch_grid, padding_grid, k, scenario.getTicks(), scenario.getParameters().getInfectionRadius()));
                id++;
            }
        }
        // add to each patch their neighbours and relevant obstacles
        for (Patch patch : patches) {
            patch.addNeighbours(patches, scenario);
            patch.addObstacles(scenario.getObstacles());
        }

        // populate each patch with relevant persons inside it
        Populate(scenario, patches);


        return patches;
    }

    /**
     * add to each Patch the relevant persons from the scenario that exists in its patch grid
     */
    private void Populate(Scenario scenario, List<Patch> patches) {

        int id = 0;
        for (PersonInfo personInfo : scenario.getPopulation()) {
            for (Patch patch : patches) {
                if (patch.getPatch_grid().contains(personInfo.getPosition())) {
                    patch.addPerson(new Person(id, patch, scenario.getParameters(), personInfo));
                    break;
                }
            }
            id++;
        }
        this.population_count = id;

    }

    private int CalculateK(int infectionRadius, int incubationTime, int padding) throws InsufficientPaddingException {

        int overall_uncertainty = 0,
                movement_uncertainty = 0,
                k = 0,
                initial_incubation_ticks = 1 + incubationTime;

        // in this list we will keep the list of persons who are at the end of our uncertain boundaries
        // at each tick, and their behaviour will only be relevant after incubation time ticks as their
        // infectious uncertainty might spread faster than the movement based uncertainty, so this list will
        // once it reaches the first incubation tick, it will keep a size of incubation time
        List<Integer> infectious_uncertain_boundaries = new ArrayList<>();

        // keep increasing k as far as our uncertainty is less than the padding
        while (overall_uncertainty < padding) {

            //add the movement uncertainty for each tick
            movement_uncertainty += 2;

            // add 1 to all the persons who were on our infectious uncertainty list
            // as they might have moved 1 column toward our patch each tick
            infectious_uncertain_boundaries.replaceAll(value -> value + 1);

            // check if the first incubation tick is reached
            initial_incubation_ticks--;

            if (initial_incubation_ticks > 0)
                // if the incubation time for the first uncertain infectious person
                // is not yet reached then it is not relevant yet and the movement
                // uncertainty will be faster and will overtake it for now
                overall_uncertainty = movement_uncertainty + infectionRadius;
            else {
                // if the incubation time ticks have passed then we check
                // for the position of hte first person in our infectious
                // uncertain list and add infection radius to it then we take the
                // maximum value between that and the movement based uncertainty
                // as our overall uncertainty

                overall_uncertainty = Math.max(
                        movement_uncertainty + infectionRadius,
                        infectious_uncertain_boundaries.get(0) + infectionRadius);
                // remove the first person on the list such that he is not relevant anymore
                // and the 2nd person effect will overtake his effect in the next tick
                // such that the list will maintain a size equal to incubation time
                // at any point after reaching this condition
                infectious_uncertain_boundaries.remove(0);
            }

            // add the current person on our uncertain borders to the uncertain infectious people
            infectious_uncertain_boundaries.add(overall_uncertainty);

            if (overall_uncertainty <= padding)
                k++;

        }

        // check the uncertain area for the first step and throw exception if it is not enough
        if (k == 0)
            throw new InsufficientPaddingException(padding);


        return k;
    }



}
