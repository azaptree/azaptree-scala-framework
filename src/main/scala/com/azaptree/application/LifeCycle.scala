package com.azaptree.application.lifecycle

sealed trait State

sealed trait Constructed extends State

sealed trait Initialized extends State

sealed trait Started extends State

sealed trait Stopped extends State