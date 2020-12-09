// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.ml.modeldownloader.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.SystemInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseMlLoggerTest {

  private static final String TEST_PROJECT_ID = "777777777777";
  public static final String API_KEY = "apiKey345646";
  public static final String APPLICATION_ID = "1:123456789:android:abcdef";
  private static final FirebaseOptions FIREBASE_OPTIONS =
      new Builder()
          .setApplicationId(APPLICATION_ID)
          .setProjectId(TEST_PROJECT_ID)
          .setApiKey(API_KEY)
          .build();

  private SystemInfo SYSTEM_INFO;

  private static final String MODEL_NAME = "MODEL_NAME_1";
  private static final String MODEL_HASH = "dsf324";
  private static final long SYSTEM_TIME = 2000;
  private static final Long DOWNLOAD_ID = 987923L;
  private static final CustomModel CUSTOM_MODEL_DOWNLOADING =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, DOWNLOAD_ID);
  private static final ModelOptions MODEL_OPTIONS =
      ModelOptions.builder()
          .setModelInfo(ModelInfo.builder().setName(MODEL_NAME).setHash(MODEL_HASH).build())
          .build();

  @Mock private SharedPreferencesUtil mockSharedPreferencesUtil;
  @Mock private DataTransportMlEventSender mockStatsSender;

  private FirebaseMlLogger mlLogger;
  private FirebaseApp app;

  @Before
  public void setUp() throws NameNotFoundException {
    MockitoAnnotations.initMocks(this);
    System.out.println("annz setup 1");
    FirebaseApp.clearInstancesForTest();
    System.out.println("annz setup 2");
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    System.out.println("annz setup 3");
    mlLogger = new FirebaseMlLogger(app, mockSharedPreferencesUtil, mockStatsSender);
    System.out.println("annz setup 4");
    SYSTEM_INFO =
        SystemInfo.builder()
            .setFirebaseProjectId(TEST_PROJECT_ID)
            .setAppId(app.getApplicationContext().getOpPackageName())
            .setApiKey(API_KEY)
            .setAppVersion(
                String.valueOf(
                    app.getApplicationContext()
                        .getPackageManager()
                        .getPackageInfo(app.getApplicationContext().getPackageName(), 0)
                        .versionCode))
            .build();

    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any()))
        .thenReturn(SYSTEM_TIME - 1000L);
    when(mockSharedPreferencesUtil.getModelDownloadCompleteTimeMs(any())).thenReturn(SYSTEM_TIME);
    doNothing().when(mockSharedPreferencesUtil).setModelDownloadCompleteTimeMs(any(), anyLong());
    when(mockSharedPreferencesUtil.getCustomModelStatsCollectionFlag()).thenReturn(true);
    SystemClock.setCurrentTimeMillis(SYSTEM_TIME + 500);
  }

  @Test
  public void loggingOff() {
    when(mockSharedPreferencesUtil.getCustomModelStatsCollectionFlag()).thenReturn(false);
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 0);
    verify(mockStatsSender, Mockito.never()).sendEvent(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadFailureWithReason() {
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 405);

    verify(mockStatsSender, Mockito.times(1))
        .sendEvent(
            eq(
                FirebaseMlLogEvent.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setRoughDownloadDurationMs(1000L)
                            .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
                            .setDownloadStatus(DownloadStatus.FAILED)
                            .setDownloadFailureStatus(405)
                            .build())
                    .setSystemInfo(SYSTEM_INFO)
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadCompleteTimeMs(any());
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadFailureWithReason_getModelDownloadBeginTimeMsNull() {
    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any())).thenReturn(0L);
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 405);

    verify(mockStatsSender, Mockito.times(1))
        .sendEvent(
            eq(
                FirebaseMlLogEvent.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
                            .setDownloadStatus(DownloadStatus.FAILED)
                            .setDownloadFailureStatus(405)
                            .build())
                    .setSystemInfo(SYSTEM_INFO)
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadEventWithExactDownloadTime() {
    mlLogger.logDownloadEventWithExactDownloadTime(
        CUSTOM_MODEL_DOWNLOADING, ErrorCode.NO_ERROR, DownloadStatus.SUCCEEDED);

    verify(mockStatsSender, Mockito.times(1))
        .sendEvent(
            eq(
                FirebaseMlLogEvent.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setExactDownloadDurationMs(1500L)
                            .setErrorCode(ErrorCode.NO_ERROR)
                            .setDownloadStatus(DownloadStatus.SUCCEEDED)
                            .build())
                    .setSystemInfo(SYSTEM_INFO)
                    .build()));
    verify(mockStatsSender, Mockito.times(1)).sendEvent(any());
    verify(mockSharedPreferencesUtil, timeout(1)).setModelDownloadCompleteTimeMs(any(), eq(2500L));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadEventWithExactDownloadTime_getModelDownloadBeginTimeMsNull() {
    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any())).thenReturn(0L);
    mlLogger.logDownloadEventWithExactDownloadTime(
        CUSTOM_MODEL_DOWNLOADING, ErrorCode.NO_ERROR, DownloadStatus.SUCCEEDED);

    verify(mockStatsSender, Mockito.times(1))
        .sendEvent(
            eq(
                FirebaseMlLogEvent.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setErrorCode(ErrorCode.NO_ERROR)
                            .setDownloadStatus(DownloadStatus.SUCCEEDED)
                            .build())
                    .setSystemInfo(SYSTEM_INFO)
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }
}
