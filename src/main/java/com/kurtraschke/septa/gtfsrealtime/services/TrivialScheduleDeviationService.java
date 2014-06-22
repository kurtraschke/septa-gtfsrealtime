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

package com.kurtraschke.septa.gtfsrealtime.services;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.util.AssertionFailedException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author kurt
 */
public class TrivialScheduleDeviationService {

    private final GeometryFactory _gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);
    private final GtfsRelationalDao _dao;

    private final Logger _log = LoggerFactory.getLogger(TrivialScheduleDeviationService.class);

    Map<AgencyAndId, TripIndex> tripToIndexMap = new HashMap<>();

    public TrivialScheduleDeviationService(GtfsRelationalDao dao) {
        _dao = dao;
    }

    public int computeDeviation(double lat, double lon, Calendar timeAtPosition, AgencyAndId tripId, ServiceDate serviceDate) {

        if (!tripToIndexMap.containsKey(tripId)) {
            tripToIndexMap.put(tripId, new TripIndex(tripId));
        }

        return tripToIndexMap.get(tripId).computeDeviation(lat, lon, timeAtPosition, serviceDate);

    }

    private static int interpolateTime(double firstPosition, double secondPosition,
            int firstTime, int secondTime, double probe) {
        double interp = ((probe - firstPosition) / (secondPosition - firstPosition)) * (secondTime - firstTime) + firstTime;

        return (int) interp;
    }

    private class TripIndex {

        private final Trip theTrip;
        private final LengthIndexedLine lil;
        private final NavigableMap<Double, StopTime> positionToStopTimeMap = new TreeMap<>();

        public TripIndex(AgencyAndId tripId) {
            theTrip = _dao.getTripForId(tripId);

            List<ShapePoint> shapePoints = new ArrayList<>(_dao.getShapePointsForShapeId(theTrip.getShapeId()));

            Collections.sort(shapePoints);

            List<Coordinate> coordinates = new ArrayList<>(shapePoints.size());

            for (ShapePoint p : shapePoints) {
                Coordinate c = new Coordinate(p.getLon(), p.getLat());
                coordinates.add(c);
            }

            LineString shapeLineString = _gf.createLineString(coordinates.toArray(new Coordinate[]{}));

            lil = new LengthIndexedLine(shapeLineString);

            List<StopTime> stopTimes = new ArrayList<>(_dao.getStopTimesForTrip(theTrip));

            Collections.sort(stopTimes);

            double lastPosition = lil.getStartIndex();

            for (StopTime st : stopTimes) {
                Coordinate stc = new Coordinate(st.getStop().getLon(), st.getStop().getLat());

                double nextPosition;

                try {
                    nextPosition = lil.indexOfAfter(stc, lastPosition);
                    positionToStopTimeMap.put(nextPosition, st);

                    lastPosition = nextPosition;

                } catch (AssertionFailedException ae) {
                    nextPosition = lil.project(stc);

                    if (nextPosition < lastPosition) {
                        _log.warn("Position " + stc + " goes backwards from index " + nextPosition + " to " + lastPosition + "; will not be used for timing");
                    }
                }
            }
        }

        public int computeDeviation(double lat, double lon, Calendar timeAtPosition, ServiceDate serviceDate) {

            double probe = lil.project(new Coordinate(lon, lat));

            Map.Entry<Double, StopTime> prevStopEntry = positionToStopTimeMap.floorEntry(probe);
            Map.Entry<Double, StopTime> nextStopEntry = positionToStopTimeMap.ceilingEntry(probe);

            if (prevStopEntry == null) {
                prevStopEntry = nextStopEntry;
                nextStopEntry = positionToStopTimeMap.ceilingEntry(prevStopEntry.getKey());
            } else if (nextStopEntry == null) {
                nextStopEntry = prevStopEntry;
                prevStopEntry = positionToStopTimeMap.floorEntry(nextStopEntry.getKey());
            }

            StopTime prevStopTime = prevStopEntry.getValue();
            StopTime nextStopTime = nextStopEntry.getValue();

            double prevPosition = prevStopEntry.getKey();
            double nextPosition = nextStopEntry.getKey();

            int prevDeparture = prevStopTime.getDepartureTime();
            int nextArrival = nextStopTime.getArrivalTime();

            int expectedTimeForPosition = interpolateTime(prevPosition, nextPosition,
                    prevDeparture, nextArrival, probe);

            int actualTimeAtPosition
                    = (int) ((timeAtPosition.getTimeInMillis()
                    - serviceDate.getAsCalendar(
                            TimeZone.getTimeZone(theTrip.getRoute().getAgency().getTimezone())
                    ).getTimeInMillis()) / 1000L);

            int deviation = expectedTimeForPosition - actualTimeAtPosition;

            return deviation;
        }
    }
}
