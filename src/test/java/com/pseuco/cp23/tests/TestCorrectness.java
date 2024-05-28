package com.pseuco.cp23.tests;

import com.pseuco.cp23.tests.common.TestCase;

import org.junit.Test;

public class TestCorrectness {

    @Test
    public void testWeLoveNP10() {
        TestCase.getPublic("we_love_np").launchRocket(10);
    }

    @Test
    public void testWeLoveNP15() {
        TestCase.getPublic("we_love_np").launchRocket(15);
    }

    @Test
    public void testMinimal (){
        TestCase.getPublic("Minimal Example").launchRocket(10);
    }

    @Test
    public void testMedium (){
        TestCase.getPublic("Medium Size").launchRocket(20);
    }


    @Test
    public void testLarge1 (){
        TestCase.getPublic("Large Size").launchRocket(10);
    }

    @Test
    public void testLarge2 (){
        TestCase.getPublic("Large Size").launchRocket(20);
    }

    @Test
    public void testLarge3 (){
        TestCase.getPublic("Large Size").launchRocket(30);
    }

    @Test
    public void testLarge4 (){
        TestCase.getPublic("Large Size").launchRocket(40);
    }

    @Test
    public void testLargeLong1 (){
        TestCase.getPublic("Large Size (Long Term)").launchRocket(10);
    }
    @Test
    public void testLargeLong2 (){
        TestCase.getPublic("Large Size (Long Term)").launchRocket(20);
    }
    @Test
    public void testLargeLong3 (){
        TestCase.getPublic("Large Size (Long Term)").launchRocket(30);
    }    @Test
    public void testLargeLong4 (){

        TestCase.getPublic("Large Size (Long Term)").launchRocket(40);
    }

    @Test
    public void testLargeLong5 (){
        TestCase.getPublic("Large Size (Long Term)2").launchRocket(7);
    }
    @Test
    public void testLargeLong6 (){
        TestCase.getPublic("Large Size (Long Term)2").launchRocket(20);
    }
    @Test
    public void testLargeLong7 (){
        TestCase.getPublic("Large Size (Long Term)2").launchRocket(30);
    }    @Test
    public void testLargeLong8 (){
        TestCase.getPublic("Large Size (Long Term)2").launchRocket(40);
    }





}