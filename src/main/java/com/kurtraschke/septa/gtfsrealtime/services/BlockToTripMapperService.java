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

import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import com.google.common.collect.Iterables;
import com.kurtraschke.septa.gtfsrealtime.model.ActivatedTrip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author kurt
 */
public class BlockToTripMapperService {

  private final int DAY_IN_SECONDS = 60 * 60 * 24;
  private final int AUTO_MAX_LOOK_BACK;
  private final GtfsRelationalDao _dao;
  private final CalendarServiceData _csd;

  private static final Logger _log = LoggerFactory.getLogger(BlockToTripMapperService.class);

  public BlockToTripMapperService(GtfsRelationalDao dao) throws IOException {
    _dao = dao;
    _csd = new CalendarServiceDataFactoryImpl(dao).createData();

    AUTO_MAX_LOOK_BACK = maxStopTime() / DAY_IN_SECONDS;
  }

  private int maxStopTime() {
    int maxStopTime = -1;
    for (StopTime t : _dao.getAllStopTimes()) {
      if (t.isArrivalTimeSet() && t.getArrivalTime() > maxStopTime) {
        maxStopTime = t.getArrivalTime();
      }

      if (t.isDepartureTimeSet() && t.getDepartureTime() > maxStopTime) {
        maxStopTime = t.getDepartureTime();
      }
    }
    return maxStopTime;
  }

  public int getAutoMaxLookBack() {
    return AUTO_MAX_LOOK_BACK;
  }

  /**
   * Identify the active trip in a block.
   *
   *
   * @param theBlock
   * @param blockActiveTime
   * @param maxLookBack
   * @return
   */
  public ActivatedTrip mapBlockToTrip(AgencyAndId theBlock,
      Calendar blockActiveTime, int maxLookBack) {
    Set<ActivatedTrip> trips = new HashSet<>();

    for (Trip t : _dao.getTripsForBlockId(theBlock)) {
      List<StopTime> tripStopTimes = _dao.getStopTimesForTrip(t);

      int tripStartTime = tripStopTimes.get(0).getArrivalTime();
      int tripEndTime = tripStopTimes.get(tripStopTimes.size() - 1).getDepartureTime();

      ServiceDate today = new ServiceDate(blockActiveTime);

      for (int i = 0; i <= maxLookBack; i++) {
        ServiceDate shifted = today.shift(-1 * i);
        Set<AgencyAndId> activeServices = _csd.getServiceIdsForDate(shifted);

        if (activeServices.contains(t.getServiceId())) {
          Calendar origin = shifted.getAsCalendar(_csd.getTimeZoneForAgencyId(theBlock.getAgencyId()));

          long when = (blockActiveTime.getTimeInMillis() - origin.getTimeInMillis()) / 1000;

          if (when >= tripStartTime && tripEndTime >= when) {
            trips.add(new ActivatedTrip(t, shifted));
          }
        }
      }
    }
    return Iterables.getOnlyElement(trips);
  }

  /*
   * public static void main(String[] args) throws IOException {
   * BlockToTripMapperService btms = new BlockToTripMapperService(new File(
   * "/Users/kurt/Downloads/septa_gtfs/google_bus.zip"));
   *
   * GregorianCalendar now = new GregorianCalendar();
   *
   * System.out.println(btms.mapBlockToTrip(new AgencyAndId(AGENCY_ID, "1459"),
   * now, btms.getAutoMaxLookBack())); }
   */
}
