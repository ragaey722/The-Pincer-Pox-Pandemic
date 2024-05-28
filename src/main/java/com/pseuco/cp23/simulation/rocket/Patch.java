package com.pseuco.cp23.simulation.rocket;

import com.pseuco.cp23.model.Rectangle;
import com.pseuco.cp23.model.Scenario;


import com.pseuco.cp23.model.XY;
import com.pseuco.cp23.simulation.common.Context;
import com.pseuco.cp23.simulation.common.Person;
import com.pseuco.cp23.simulation.common.Utils;
import com.pseuco.cp23.validator.Validator;

import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Patch extends Thread implements Context {

    private final int patch_id;
    private final Rectangle patch_grid;
    private final Rectangle padding_grid;
    private final List<Person> patch_population;
    private List<Person> padding_population;

    private final List<Person> whole_population;
    private List<Patch> neighbours;

    private List<Rectangle> padding_obstacles;
    private CountDownLatch countdown_synced_patches;
    private final Lock lock = new ReentrantLock();
    private final Condition wait_to_sync = lock.newCondition();

    private final int k;
    private final Validator validator;

    int current_tick;
    private final int max_ticks;

    private final int infectionRadius;


    private final BlockingQueue<Pair> results_queue;

    /**
     * @param results_queue The queue which people's traces at every tick will be transferred from this thread to the main one
     */
    public Patch(int patch_id, BlockingQueue<Pair> results_queue, Validator validator, Rectangle patch_grid, Rectangle padding_grid, int k, int max_ticks, int infectionRadius) {
        this.patch_id = patch_id;
        this.results_queue = results_queue;
        this.validator = validator;
        this.patch_grid = patch_grid;
        this.padding_grid = padding_grid;
        this.k = k;
        this.max_ticks = max_ticks;
        this.infectionRadius = infectionRadius;

        this.patch_population = new ArrayList<>();
        this.whole_population = new ArrayList<>();
        this.neighbours = new ArrayList<>();
        this.countdown_synced_patches = new CountDownLatch(0);
        this.current_tick = 0;

    }

    /**
     * This method is used during the initialization of the patch threads to populate them with relevant persons
     *
     * @param person A newly created person from the scenario who exists inside this patch grid
     */
    public void addPerson(Person person) {
        this.patch_population.add(person);
    }

    /**
     * This method checks for the relevant obstacles inside the padding grid of this patch
     *
     * @param all_obstacles the list of obstacles in the whole grid
     */
    public void addObstacles(List<Rectangle> all_obstacles) {

        this.padding_obstacles = all_obstacles.stream()
                .filter(this.padding_grid::overlaps)
                .collect(Collectors.toList());

    }

    /**
     * This method checks which other patches overlap with this padding grid
     * and may propagate information to and add them to the list of neighbours
     * that we will need to sync with later
     *
     * @param patches  the list of all patches
     * @param scenario this is passed to the mayPropagateFrom method
     */
    public void addNeighbours(List<Patch> patches, Scenario scenario) {

        this.neighbours = patches.parallelStream()
                .filter(patch -> this != patch) // Exclude the current patch
                .filter(patch -> this.padding_grid.overlaps(patch.getPatch_grid()))
                .filter(patch -> Utils.mayPropagateFrom(scenario, patch.getPatch_grid(), this.patch_grid))
                .collect(Collectors.toList());

        // this count down latch is used to check whether all the neighbours
        // have synced with this thread or not, and then wait till they all did and then proceed
        this.countdown_synced_patches = new CountDownLatch(this.neighbours.size());
    }


    @Override
    public void run() {

        for (; current_tick < max_ticks; current_tick++) {

            // Send to the main thread the relevant list of people
            // for statistics at tick 0
            if (current_tick == 0) {
                List<Person> trace_list = new ArrayList<>();
                for (Person person : patch_population)
                    trace_list.add(person.clone(this));
                try {
                    results_queue.put(new Pair(0, trace_list));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // Time to sync with other neighbours
            if (current_tick % k == 0) {

                // Signal all the other threads that wanted to sync with this one
                // but were waiting for it to reach the same tick
                lock.lock();
                wait_to_sync.signalAll();
                lock.unlock();

                // Forget about the old padding population and
                // sync with all the neighbours and add persons
                // they return to the new padding population
                padding_population = neighbours.parallelStream()
                        .flatMap(neighbour -> neighbour.Sync(this, current_tick).stream())
                        .collect(Collectors.toList());

                // reset whole population to include the new people
                // in the padding after syncing
                whole_population.clear();
                whole_population.addAll(padding_population);
                whole_population.addAll(patch_population);
                whole_population.sort(new Person.PersonIDComparator());

                // keep waiting till all neighbours are also synced with us
                // and proceed only when the countdown latch hit 0
                try {
                    countdown_synced_patches.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // initialize a new countdown latch for the next sync
                countdown_synced_patches = new CountDownLatch(neighbours.size());
            }
            // perform a tick
            this.tick();
        }


    }


    private void tick() {

        validator.onPatchTick(this.current_tick, this.patch_id);
        for (Person person : this.getPopulation()) {
            validator.onPersonTick(this.current_tick, this.patch_id, person.getId());
            person.tick();
        }


        List<Person> population = this.getPopulation();

        // go through the whole population add people on the patch grid
        // to the patch population otherwise to the padding population
        // this handles the movement of person from the padding to
        // the patch and vice versa

        this.patch_population.clear();
        this.padding_population.clear();
        for (Person person : population) {
            if (this.patch_grid.contains(person.getPosition()))
                patch_population.add(person);
            else {
                padding_population.add(person);
            }
        }

        population.forEach(Person::bustGhost);


        for (int i = 0; i < population.size(); i++) {
            for (int j = i + 1; j < population.size(); j++) {
                final Person iPerson = population.get(i);
                final Person jPerson = population.get(j);
                final XY iPosition = iPerson.getPosition();
                final XY jPosition = jPerson.getPosition();
                final int deltaX = Math.abs(iPosition.getX() - jPosition.getX());
                final int deltaY = Math.abs(iPosition.getY() - jPosition.getY());
                final int distance = deltaX + deltaY;
                if (distance <= infectionRadius) {
                    if (iPerson.isInfectious() && iPerson.isCoughing() && jPerson.isBreathing()) {
                        jPerson.infect();
                    }
                    if (jPerson.isInfectious() && jPerson.isCoughing() && iPerson.isBreathing()) {
                        iPerson.infect();
                    }
                }
            }
        }

        // send to the main thread the relevant list of people
        // for statistics at the current tick
        List<Person> trace_list = new ArrayList<>();
        for (Person person : patch_population)
            trace_list.add(person.clone(this));
        try {
            results_queue.put(new Pair(current_tick + 1, trace_list));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * @param caller_patch The patch that wants to sync with this one
     * @param current_tick The current tick the caller patch is at
     * @return The list of person inside this patch and the padding of the caller patch
     */
    public List<Person> Sync(Patch caller_patch, int current_tick) {
        lock.lock();
        try {
            // wait till this patch thread is on the same tick as the caller patch
            while (this.current_tick != current_tick)
                wait_to_sync.await();

            // clone the relevant people for the caller patch into a list
            return this.patch_population.stream()
                    .filter(person -> caller_patch.getGrid().contains(person.getPosition()))
                    .map(person -> person.clone(caller_patch))
                    .collect(Collectors.toList());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
            // decrement the countdown latch as one neighbour is done
            // syncing with this patch
            this.countdown_synced_patches.countDown();
        }
    }

    /**
     * @return the padding grid
     */
    @Override
    public Rectangle getGrid() {
        return this.padding_grid;
    }

    /**
     * @return the patch grid
     */
    public Rectangle getPatch_grid() {
        return this.patch_grid;
    }

    /**
     * @return the list of obstacles inside this padding
     */
    @Override
    public List<Rectangle> getObstacles() {
        return this.padding_obstacles;
    }

    /**
     * @return the whole population of the patch and padding
     */
    @Override
    public List<Person> getPopulation() {

        return this.whole_population;
    }


}


