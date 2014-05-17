/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kurtraschke.septa.gtfsrealtime.model;

public class Train {

  private final double latitude;
  private final double longitude;
  private final String trainNumber;
  private final String service;
  private final String destination;
  private final String nextStop;
  private final int late;
  private final String source;

  public Train(double latitude, double longitude, String trainNumber,
      String service, String destination, String nextStop, int late,
      String source) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.trainNumber = trainNumber;
    this.service = service;
    this.destination = destination;
    this.nextStop = nextStop;
    this.late = late;
    this.source = source;
  }

  public double getLatitude() {
    return latitude;
  }
  public double getLongitude() {
    return longitude;
  }
  public String getTrainNumber() {
    return trainNumber;
  }
  public String getService() {
    return service;
  }
  public String getDestination() {
    return destination;
  }
  public String getNextStop() {
    return nextStop;
  }
  public int getLate() {
    return late;
  }
  public String getSource() {
    return source;
  }

  @Override
  public String toString() {
    return "Train [latitude=" + latitude + ", longitude=" + longitude
        + ", trainNumber=" + trainNumber + ", service=" + service
        + ", destination=" + destination + ", nextStop=" + nextStop + ", late="
        + late + ", source=" + source + "]";
  }



}
