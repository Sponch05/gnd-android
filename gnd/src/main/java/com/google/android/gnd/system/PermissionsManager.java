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

package com.google.android.gnd.system;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.google.android.gnd.rx.RxCompletable;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PermissionsManager {
  private static final String TAG = PermissionsManager.class.getSimpleName();
  private static final int PERMISSIONS_REQUEST_CODE = PermissionsManager.class.hashCode() & 0xffff;

  private final Context context;
  private final Subject<PermissionsRequest> permissionsRequestSubject;
  private final Subject<PermissionsResult> permissionsResultSubject;

  @Inject
  public PermissionsManager(Application app) {
    permissionsRequestSubject = PublishSubject.create();
    permissionsResultSubject = PublishSubject.create();
    context = app.getApplicationContext();
  }

  public Observable<PermissionsRequest> getPermissionsRequests() {
    return permissionsRequestSubject;
  }

  /** Callback for use from onRequestPermissionsResult() in Activity. */
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode != PERMISSIONS_REQUEST_CODE) {
      return;
    }
    for (int i = 0; i < permissions.length; i++) {
      permissionsResultSubject.onNext(new PermissionsResult(permissions[i], grantResults[i]));
    }
  }

  public Completable obtainPermission(String permission) {
    return RxCompletable.completeIf(() -> requestPermission(permission))
        .ambWith(getPermissionsResult(permission));
  }

  private boolean requestPermission(String permission) {
    if (isGranted(permission)) {
      Log.d(TAG, permission + " already granted");
      return true;
    } else {
      Log.i(TAG, "Requesting " + permission);
      permissionsRequestSubject.onNext(
          new PermissionsRequest(PERMISSIONS_REQUEST_CODE, new String[] {permission}));
      return false;
    }
  }

  private Completable getPermissionsResult(String permission) {
    return permissionsResultSubject
        .filter(r -> r.getPermission().equals(permission))
        .take(1)
        .singleOrError()
        .flatMapCompletable(PermissionsResult::toCompletable);
  }

  private boolean isGranted(String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static class PermissionsRequest {
    private int requestCode;
    private String[] permissions;

    private PermissionsRequest(int requestCode, String[] permissions) {
      this.requestCode = requestCode;
      this.permissions = permissions;
    }

    public int getRequestCode() {
      return requestCode;
    }

    public String[] getPermissions() {
      return permissions;
    }
  }

  private static class PermissionsResult {
    private String permission;
    private int grantResult;

    private PermissionsResult(String permission, int grantResult) {
      this.permission = permission;
      this.grantResult = grantResult;
    }

    public String getPermission() {
      return permission;
    }

    public static CompletableSource toCompletable(PermissionsResult result) {
      return result.isGranted()
          ? Completable.complete()
          : Completable.error(new PermissionDeniedException());
    }

    @Override
    public String toString() {
      return permission + " granted = " + isGranted();
    }

    private boolean isGranted() {
      return grantResult == PackageManager.PERMISSION_GRANTED;
    }
  }

  public static class PermissionDeniedException extends Exception {}
}
