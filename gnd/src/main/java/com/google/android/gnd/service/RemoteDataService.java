/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.service;

import com.google.android.gnd.vo.Place;
import com.google.android.gnd.vo.PlaceUpdate.RecordUpdate.ValueUpdate;
import com.google.android.gnd.vo.Project;
import com.google.android.gnd.vo.Record;
import com.google.common.collect.ImmutableList;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.util.List;

/**
 * Data service is treated as if it's remote, though implementations may cache data locally as well.
 */
public interface RemoteDataService {
  Single<List<Project>> loadProjectSummaries();

  Single<Project> loadProject(String projectId);

  Flowable<DatastoreEvent<Place>> getPlaceVectorStream(Project project);

  Single<List<Record>> loadRecordSummaries(Place place);

  Single<Record> loadRecordDetails(Place place, String recordId);

  Single<Record> saveChanges(
    Record record,
    ImmutableList<ValueUpdate> updates);

  Single<Place> addPlace(Place place);
}
