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

public class Bus {

  private final double latitude;
  private final double longitude;
  private final String label;
  private final String vehicleId;
  private final String blockId;
  private final String direction;
  private final String destination;
  private final int offset;

  public Bus(double latitude, double longitude, String label, String vehicleId,
      String blockId, String direction, String destination, int offset) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.label = label;
    this.vehicleId = vehicleId;
    this.blockId = blockId;
    this.direction = direction;
    this.destination = destination;
    this.offset = offset;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public String getLabel() {
    return label;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public String getBlockId() {
    return blockId;
  }

  public String getDirection() {
    return direction;
  }

  public String getDestination() {
    return destination;
  }

  public int getOffset() {
    return offset;
  }

  @Override
  public String toString() {
    return "Bus [latitude=" + latitude + ", longitude=" + longitude
        + ", label=" + label + ", vehicleId=" + vehicleId + ", blockId="
        + blockId + ", direction=" + direction + ", destination=" + destination
        + ", offset=" + offset + "]";
  }

}
