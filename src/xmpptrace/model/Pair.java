/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.model;

/**
 * Simple "pair of somethings" template.  
 * 
 * @author adb
 *
 * @param <A> Something
 * @param <B> Something else
 */
public class Pair<A, B> 
{
	public A first;
	public B second;
}