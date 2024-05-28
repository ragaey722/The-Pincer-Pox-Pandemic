package com.pseuco.cp23.tests;

import com.pseuco.cp23.tests.common.TestCase;

import org.junit.Test;

public class TestSlug {
    @Test
    public void testWeLoveNP() {
        TestCase.getPublic("we_love_np").runSlug();
    }
}