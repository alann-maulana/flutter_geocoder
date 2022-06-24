package com.aloisdeniel.geocoder;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.lang.Exception;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * NotAvailableException
 */
class NotAvailableException extends Exception {
  NotAvailableException() {}
}

/**
 * GeocoderPlugin
 */
public class GeocoderPlugin implements FlutterPlugin, MethodCallHandler {

  private Geocoder geocoder;
  private MethodChannel channel;

  /**
   * Plugin registration.
   */
  public static void registerWith(@SuppressWarnings("deprecation") Registrar registrar) {
    GeocoderPlugin plugin = new GeocoderPlugin();
    plugin.setupChannels(registrar.context(), registrar.messenger());
  }

  private void setupChannels(Context context, BinaryMessenger binaryMessenger) {
    this.geocoder = new Geocoder(context);

    channel = new MethodChannel(binaryMessenger, "github.com/aloisdeniel/geocoder");
    channel.setMethodCallHandler(this);
  }

  private void teardownChannels() {
    geocoder = null;

    channel.setMethodCallHandler(null);
    channel = null;
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    setupChannels(binding.getApplicationContext(), binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    teardownChannels();
  }

  // MethodChannel.Result wrapper that responds on the platform thread.
  private static class MethodResultWrapper implements Result {
    private final Result methodResult;
    private final Handler handler;

    MethodResultWrapper(Result result) {
      methodResult = result;
      handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void success(final Object result) {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              methodResult.success(result);
            }
          });
    }

    @Override
    public void error(
        final String errorCode, final String errorMessage, final Object errorDetails) {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              methodResult.error(errorCode, errorMessage, errorDetails);
            }
          });
    }

    @Override
    public void notImplemented() {
      handler.post(
          new Runnable() {
            @Override
            public void run() {
              methodResult.notImplemented();
            }
          });
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void onMethodCall(MethodCall call, @NonNull Result rawResult) {
    Result result = new MethodResultWrapper(rawResult);

    if (call.method.equals("findAddressesFromQuery")) {
      String address = (String) call.argument("address");
      findAddressesFromQuery(address, result);
    }
    else if (call.method.equals("findAddressesFromCoordinates")) {
      float latitude = call.<Number>argument("latitude").floatValue();
      float longitude = call.<Number>argument("longitude").floatValue();
      findAddressesFromCoordinates(latitude,longitude, result);
    } else {
      result.notImplemented();
    }
  }

  private void assertPresent() throws NotAvailableException {
    if (!Geocoder.isPresent()) {
      throw new NotAvailableException();
    }
  }

  private void findAddressesFromQuery(final String address, final Result result) {

    ExecutorService executor = Executors.newSingleThreadExecutor();
    final Handler handler = new Handler(Looper.getMainLooper());

    executor.execute(new Runnable() {
      @Override
      public void run() {
        List<Address> results;
        try {
          assertPresent();
          results = geocoder.getFromLocationName(address, 20);
        } catch (IOException ex) {
          results = null;
        } catch (NotAvailableException ex) {
          results = new ArrayList<>();
        }

        final List<Address> addresses = results;
        handler.post(new Runnable() {
          @Override
          public void run() {
            if (addresses != null) {
              if (addresses.isEmpty()) {
                result.error("not_available", "Empty", null);
              } else {
                result.success(createAddressMapList(addresses));
              }
            } else {
              result.error("failed", "Failed", null);
            }
          }
        });
      }
    });
  }

  private void findAddressesFromCoordinates(final float latitude, final float longitude, final Result result) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    final Handler handler = new Handler(Looper.getMainLooper());

    executor.execute(new Runnable() {
      @Override
      public void run() {
        List<Address> results;
        try {
          assertPresent();
          results = geocoder.getFromLocation(latitude, longitude, 20);
        } catch (IOException ex) {
          results = null;
        } catch (NotAvailableException ex) {
          results = new ArrayList<>();
        }

        final List<Address> addresses = results;
        handler.post(new Runnable() {
          @Override
          public void run() {
            if (addresses != null) {
              if (addresses.isEmpty()) {
                result.error("not_available", "Empty", null);
              } else {
                result.success(createAddressMapList(addresses));
              }
            } else {
              result.error("failed", "Failed", null);
            }
          }
        });
      }
    });
  }

  private Map<String, Object> createCoordinatesMap(Address address) {

    if(address == null)
      return null;

    Map<String, Object> result = new HashMap<>();

    result.put("latitude", address.getLatitude());
    result.put("longitude", address.getLongitude());

    return result;
  }

  private Map<String, Object> createAddressMap(Address address) {

    if(address == null)
      return null;

    // Creating formatted address
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(address.getAddressLine(i));
    }

    Map<String, Object> result = new HashMap<>();

    result.put("coordinates", createCoordinatesMap(address));
    result.put("featureName", address.getFeatureName());
    result.put("countryName", address.getCountryName());
    result.put("countryCode", address.getCountryCode());
    result.put("locality", address.getLocality());
    result.put("subLocality", address.getSubLocality());
    result.put("thoroughfare", address.getThoroughfare());
    result.put("subThoroughfare", address.getSubThoroughfare());
    result.put("adminArea", address.getAdminArea());
    result.put("subAdminArea", address.getSubAdminArea());
    result.put("addressLine", sb.toString());
    result.put("postalCode", address.getPostalCode());

    return result;
  }

  private List<Map<String, Object>> createAddressMapList(List<Address> addresses) {

    if(addresses == null)
      return new ArrayList<>();

    List<Map<String, Object>> result = new ArrayList<>(addresses.size());

    for (Address address : addresses) {
      result.add(createAddressMap(address));
    }

    return result;
  }
}

