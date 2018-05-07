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

package com.google.android.gnd.ui.mapcontainer;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import butterknife.BindView;
import com.google.android.gnd.R;
import com.google.android.gnd.model.PlaceIcon;
import com.google.android.gnd.model.Point;
import com.google.android.gnd.model.ProjectActivationEvent;
import com.google.android.gnd.system.PermissionsManager.PermissionDeniedException;
import com.google.android.gnd.system.SettingsManager.SettingsChangeRequestCanceled;
import com.google.android.gnd.ui.common.GndFragment;
import com.google.android.gnd.ui.map.MapAdapter;
import com.google.android.gnd.ui.map.MapAdapter.MapViewModel;
import com.google.android.gnd.ui.map.MapMarker;
import com.google.android.gnd.ui.mapcontainer.MapContainerViewModel.LocationLockStatus;
import com.jakewharton.rxbinding2.view.RxView;
import javax.inject.Inject;

/** Main app view, displaying the map and related controls (center cross-hairs, add button, etc). */
public class MapContainerFragment extends GndFragment {
  private static final String TAG = MapContainerFragment.class.getSimpleName();

  @Inject
  ViewModelProvider.Factory viewModelFactory;

  @Inject
  AddPlaceDialogFragment addPlaceDialogFragment;

  @Inject
  MapAdapter mapAdapter;

  @BindView(R.id.add_place_btn)
  FloatingActionButton addPlaceBtn;

  @BindView(R.id.location_lock_btn)
  FloatingActionButton locationLockBtn;

  private MapContainerViewModel viewModel;

  @Inject
  public MapContainerFragment() {
  }

  @Override
  protected void onCreateViewModel() {
    viewModel = ViewModelProviders.of(this, viewModelFactory).get(MapContainerViewModel.class);
  }

  @Override
  protected void onBindViews() {
    disableLocationLockBtn();
    disableAddPlaceBtn();
  }

  @Override
  protected View onInflateView(
    LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_map, container, false);
  }

  @Override
  protected void onAddFragments() {
    addFragment(R.id.map, mapAdapter.getFragment());
  }

  @Override
  protected void onObserveViewModel() {
    mapAdapter.getViewModel().subscribe(this::onMapReady);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  private void onMapReady(MapViewModel map) {
    Log.d(TAG, "Map ready. Updating subscriptions");
    // Observe events emitted by the ViewModel.
    viewModel.mapMarkers().observe(this, update -> onMarkerUpdate(map, update));
    viewModel.locationLockStatus().observe(this, this::onLocationLockStatusChange);
    viewModel.cameraUpdates().observe(this, this::onCameraUpdate);
    viewModel.projectActivationEvents().observe(this, this::onProjectActivationEvent);
    // Pass UI events to the ViewModel.
    // TODO: Route "add place" action through an interactor and down to dialog instead of binding
    // here to implement "Clean Architecture".
    RxView.clicks(addPlaceBtn).subscribe(__ -> showAddPlaceDialog(map.getCenter()));
    RxView.clicks(locationLockBtn).subscribe(__ -> viewModel.onLocationLockClick());
    map.markerClicks().subscribe(viewModel::onMarkerClick);
    map.dragInteractions().subscribe(viewModel::onMapDrag);
    enableLocationLockBtn();
  }

  private void onProjectActivationEvent(ProjectActivationEvent projectActivationEvent) {
    if (projectActivationEvent.isActivated()) {
      enableAddPlaceBtn();
    } else {
      disableAddPlaceBtn();
    }
  }

  private void enableLocationLockBtn() {
    locationLockBtn.setEnabled(true);
  }

  private void disableLocationLockBtn() {
    locationLockBtn.setEnabled(false);
  }

  private void enableAddPlaceBtn() {
    addPlaceBtn.setEnabled(true);
    addPlaceBtn.setBackgroundTintList(
      ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
  }

  private void disableAddPlaceBtn() {
    addPlaceBtn.setEnabled(false);
    addPlaceBtn.setBackgroundTintList(
      ColorStateList.valueOf(getResources().getColor(R.color.colorGrey500)));
  }

  private void onLocationLockStatusChange(LocationLockStatus status) {
    if (status.isError()) {
      onLocationLockError(status.getError());
    }
    if (status.isEnabled()) {
      Log.d(TAG, "Location lock enabled");
      mapAdapter.getViewModel().subscribe(map -> map.enableCurrentLocationIndicator());
      locationLockBtn.setImageResource(R.drawable.ic_gps_blue);
    } else {
      Log.d(TAG, "Location lock disabled");
      locationLockBtn.setImageResource(R.drawable.ic_gps_grey600);
    }
  }

  private void onLocationLockError(Throwable t) {
    if (t instanceof PermissionDeniedException) {
      showUserActionFailureMessage(R.string.no_fine_location_permissions);
    } else if (t instanceof SettingsChangeRequestCanceled) {
      showUserActionFailureMessage(R.string.location_disabled_in_settings);
    } else {
      showUserActionFailureMessage(R.string.location_updates_unknown_error);
    }
  }

  private void showUserActionFailureMessage(int resId) {
    Toast.makeText(getContext(), resId, Toast.LENGTH_LONG).show();
  }

  @SuppressLint("CheckResult")
  private void onCameraUpdate(MapContainerViewModel.CameraUpdate update) {
    Log.d(TAG, "Update camera: " + update);
    mapAdapter
      .getViewModel()
      .subscribe(
        map -> {
          if (update.getZoomLevel().isPresent()) {
            map.moveCamera(update.getCenter(), update.getZoomLevel().get());
          } else {
            map.moveCamera(update.getCenter());
          }
        });
  }

  private void showAddPlaceDialog(Point location) {
    // TODO: Pause location updates while dialog is open.
    addPlaceDialogFragment.show(getFragmentManager(), location).subscribe(viewModel::onAddPlace);
  }

  private void onMarkerUpdate(MapViewModel map, MarkerUpdate update) {
    switch (update.getType()) {
      case CLEAR_ALL:
        map.removeAllMarkers();
        break;
      case ADD_OR_UPDATE_MARKER:
        PlaceIcon icon = new PlaceIcon(getContext(), update.getIconId(), update.getIconColor());
        map.addOrUpdateMarker(
          new MapMarker<>(update.getId(), update.getPlace().getPoint(), icon, update.getPlace()),
          update.hasPendingWrites(),
          false);
        break;
      case REMOVE_MARKER:
        map.removeMarker(update.getId());
        break;
    }
  }
}