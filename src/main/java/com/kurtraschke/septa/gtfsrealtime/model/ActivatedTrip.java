/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.kurtraschke.septa.gtfsrealtime.model;

import java.util.Objects;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

/**
 *
 * @author kurt
 */
public class ActivatedTrip {

    private final Trip trip;
    private final ServiceDate serviceDate;

    public ActivatedTrip(Trip trip, ServiceDate serviceDate) {
        this.trip = trip;
        this.serviceDate = serviceDate;
    }

    /**
     * @return the trip
     */
    public Trip getTrip() {
        return trip;
    }

    /**
     * @return the serviceDate
     */
    public ServiceDate getServiceDate() {
        return serviceDate;
    }

    @Override
    public String toString() {
        return "ActivatedTrip{" + "trip=" + trip + ", serviceDate=" + serviceDate + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.trip);
        hash = 89 * hash + Objects.hashCode(this.serviceDate);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ActivatedTrip other = (ActivatedTrip) obj;
        if (!Objects.equals(this.trip, other.trip)) {
            return false;
        }
        if (!Objects.equals(this.serviceDate, other.serviceDate)) {
            return false;
        }
        return true;
    }
}
