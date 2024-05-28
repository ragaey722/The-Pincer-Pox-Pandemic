package com.pseuco.cp23.simulation.common;

import com.pseuco.cp23.model.Output;

/**
 * A common interface to be implemented by simulation engines.
 */
public interface Simulation extends Runnable {
    public Output getOutput();
}