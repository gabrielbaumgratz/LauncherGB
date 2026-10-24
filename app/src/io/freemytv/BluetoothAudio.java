package io.freemytv;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fast 1-click Bluetooth audio connector for TV headphones and speakers.
 */
final class BluetoothAudio {
    private BluetoothAudio() {}

    static class DeviceInfo {
        final BluetoothDevice device;
        final String name;
        final boolean isConnected;

        DeviceInfo(BluetoothDevice device, String name, boolean isConnected) {
            this.device = device;
            this.name = name != null && !name.isEmpty() ? name : "Bluetooth";
            this.isConnected = isConnected;
        }
    }

    interface ConnectCallback {
        void onResult(boolean success);
    }

    /** Returns list of paired audio devices (headphones, soundbars, earbuds). */
    static List<DeviceInfo> getPairedAudioDevices() {
        List<DeviceInfo> list = new ArrayList<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return list;

            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null) return list;

            for (BluetoothDevice d : bonded) {
                BluetoothClass bc = d.getBluetoothClass();
                int major = bc != null ? bc.getMajorDeviceClass() : BluetoothClass.Device.Major.UNCATEGORIZED;
                if (major == BluetoothClass.Device.Major.AUDIO_VIDEO || major == BluetoothClass.Device.Major.UNCATEGORIZED) {
                    list.add(new DeviceInfo(d, d.getName(), isDeviceConnected(d)));
                }
            }

            // Connected first, then by name.
            java.util.Collections.sort(list, new java.util.Comparator<DeviceInfo>() {
                @Override public int compare(DeviceInfo x, DeviceInfo y) {
                    if (x.isConnected != y.isConnected) return x.isConnected ? -1 : 1;
                    return x.name.compareToIgnoreCase(y.name);
                }
            });
        } catch (Exception e) {
            AppLog.w("bt: " + e.getMessage());
        }
        return list;
    }

    private static boolean isDeviceConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected");
            Object res = m.invoke(device);
            return res instanceof Boolean && (Boolean) res;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Connects to audio device in 1 click using A2DP profile. */
    static void connect(Context c, BluetoothDevice device, ConnectCallback cb) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                if (cb != null) cb.onResult(false);
                return;
            }

            adapter.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    boolean ok = false;
                    try {
                        Method connectMethod = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                        connectMethod.setAccessible(true);
                        Object res = connectMethod.invoke(proxy, device);
                        ok = res instanceof Boolean && (Boolean) res;
                    } catch (Exception e) {
                        AppLog.w("a2dp connect: " + e.getMessage());
                    } finally {
                        adapter.closeProfileProxy(profile, proxy);
                    }
                    if (cb != null) cb.onResult(ok);
                }

                @Override
                public void onServiceDisconnected(int profile) {}
            }, BluetoothProfile.A2DP);
        } catch (Exception e) {
            AppLog.w("connectAudio: " + e.getMessage());
            if (cb != null) cb.onResult(false);
        }
    }

    /** Disconnects an audio device. */
    static void disconnect(Context c, BluetoothDevice device, ConnectCallback cb) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return;
            adapter.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        Method disconnectMethod = proxy.getClass().getMethod("disconnect", BluetoothDevice.class);
                        disconnectMethod.setAccessible(true);
                        disconnectMethod.invoke(proxy, device);
                    } catch (Exception ignored) {}
                    finally {
                        adapter.closeProfileProxy(profile, proxy);
                    }
                    if (cb != null) cb.onResult(true);
                }

                @Override
                public void onServiceDisconnected(int profile) {}
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) {}
    }
}
