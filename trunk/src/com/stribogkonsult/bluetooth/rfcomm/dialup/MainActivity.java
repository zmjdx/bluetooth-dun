package com.stribogkonsult.bluetooth.rfcomm.dialup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.stribogkonsult.bluetooth.rfcomm.dialup.R;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;

public class MainActivity extends Activity {

	private enum DUNStatus {
		Unknown, Connected, Disconnected
	};

	private DUNStatus connectionStatus = DUNStatus.Unknown;
	private static final String TAG = "Bluetoothpppd";
	private static final boolean D = false;
	private static final String myDigit = "243";
	private Handler handler = new Handler();
	private TextView outputView;
	private String mPackName;
	private TextView mConnectionStatus;

	// private EditText mEditAccessPoint;
	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
	private static final int REQUEST_ENABLE_BT = 3;
	private BluetoothAdapter mBluetoothAdapter = null;
	private String mSU;
	private String mRFCOMM;
	private String mPPPD;
	private String mChatName;
	private Date mTheLastTime;
	private Timer mStatusTimer;
	private Long lLastms = (long) 0;

	/** Called when the activity is first created. */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
		case R.id.menu_connect:
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
			return true;
		case R.id.menu_disconnect:
			RunDisConnectionInThread();
			return true;
		case R.id.menu_about:
			serverIntent = new Intent(this, AboutDUN.class);
			startActivity(serverIntent);
			return true;
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE_SECURE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				RunConnectionInThread(data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS));
			}
			break;
		}
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
	}

	@SuppressWarnings("unused")
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
		if (false) {
			RunDisConnectionInThread();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		mStatusTimer.cancel();
		finish();

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mform);

		outputView = (TextView) findViewById(R.id.outputView);
		mConnectionStatus = (TextView) findViewById(R.id.connectionStatus2);
		mPackName = getPackageName();
		findViewById(R.id.runScript).setOnClickListener(onButtonConnect);
		findViewById(R.id.disconnect).setOnClickListener(onButtonDisConnect);
		// mEditAccessPoint = (EditText)
		// findViewById(R.id.editText_access_point);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		mSU = "su";
		mRFCOMM = "rfcomm";
		if (!ExecutiveExists(mRFCOMM)) {
			mRFCOMM = "/data/data/" + mPackName + "/rfcomm";
			File file = new File(mRFCOMM);
			if(!file.exists()){
				AssetCopy("rfcomm", mRFCOMM);
			}
		}

		mChatName = "chat";
		if (!ExecutiveExists(mChatName)) {
			mChatName = "/data/data/" + mPackName + "/chat";
			File file = new File(mChatName);
			if(!file.exists()){
				AssetCopy("chat", mChatName);
			}
		}

		mPPPD = "pppd";
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		mStatusTimer = new Timer();
		mStatusTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				statusTimerMethod();
			}

		}, 0, 10000);
		mTheLastTime = new Date();
		lLastms = (long) 0;
	}

	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
	}

	private void statusTimerMethod() {
		this.runOnUiThread(statusTimer_Tick);
	}

	private Runnable statusTimer_Tick = new Runnable() {
		public void run() {
			displayConnection();
		}
	};

	private void displayConnection() {
		String IP = FindIP();
		if (IP != null) {
			mConnectionStatus.setText("IP is: " + IP.substring(1));
			if (connectionStatus != DUNStatus.Connected) {
				connectionStatus = DUNStatus.Connected;
				parseLongLogcat();
			}
		} else {
			mConnectionStatus.setText("Not connected");
			if (connectionStatus != DUNStatus.Disconnected) {
				connectionStatus = DUNStatus.Disconnected;
				parseLongLogcat();
			}
		}
	}

	private int atoi(String s) {
		if (s != null && s.length() > 0) {
			return Integer.parseInt(s);
		}
		return 0;
	}

	public void parseLongLogcat() {
		String out = Commands2script(
				new String[] { "logcat -v long -d -s pppd" }, "logcatscript");
		String lines[] = out.split("\n");
		Pattern p = Pattern
				.compile("\\[\\s+(\\d\\d)-(\\d\\d)\\s+(\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d+)\\s+(\\d+):(0x\\w+)\\s+[I|D|E]\\/pppd\\s+\\]");
		String log = "";
		boolean fMustDisplay = true;
		for (int i = 0; i < lines.length; i++) {
			Matcher m = p.matcher(lines[i]);
			if (D)
				Log.d(TAG, lines[i]);

			if (m.matches()) {
				Date d = new Date(mTheLastTime.getYear(), atoi(m.group(1)) - 1,
						atoi(m.group(2)), atoi(m.group(3)), atoi(m.group(4)),
						atoi(m.group(5)));
				Long l = d.getTime() + atoi(m.group(5));
				if (l >= lLastms) {
					lLastms = l;
					log += lines[++i] + "\n";
				}
			}
		}
		if (fMustDisplay) {
			outputInfo(log);
		}

	}

	private OnClickListener onButtonDisConnect = new OnClickListener() {
		public void onClick(View v) {
			RunDisConnectionInThread();
		}
	};

	private OnClickListener onButtonConnect = new OnClickListener() {
		public void onClick(View v) {
			Intent serverIntent = null;
			RunDisConnectionInThread();
			serverIntent = new Intent(getApplicationContext(),
					DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);

		}
	};

	private String Commands2script(String[] cmdLine, String... aScriptNames) {
		String aScriptName = "script.sh";
		if (aScriptNames.length > 0) {
			aScriptName = aScriptNames[0];
		}
		FileWriter out = null;
		String scriptName = "/data/data/" + mPackName + "/" + aScriptName;
		File file = new File(scriptName);
		if (file.exists()) {
			file.delete();
		}
		if (D)
			Log.d(TAG, "Create");
		try {
			file.createNewFile();
			file.setExecutable(true, false);
			file.setWritable(true, false);
			file.setReadable(true, false);
			out = new FileWriter(file);
			out.write("#! /system/bin/sh" + "\n");
			for (String s : cmdLine) {
				out.write(s + "\n");
				if (D)
					Log.d(TAG, s);
			}
			out.write("\n");
			out.close();
			String s = exec(mSU + " -c " + scriptName);
			if (!D)
				file.delete();
			return s;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	private String FindChannel(String output) {
		String[] lines = output.split("\n");
		boolean dunWasFound = false;
		for (String line : lines) {
			String[] part = line.split(":");
			for (int i = 0; i < part.length; i++) {
				if (part[i] != null) {
					part[i] = part[i].trim();
				}
			}
			if (part[0].equalsIgnoreCase("Service Name")
					&& part[1].equalsIgnoreCase("Dial-up networking")) {
				if (true == dunWasFound) {
					return null;
				}
				dunWasFound = true;
			} else if (true == dunWasFound
					&& part[0].equalsIgnoreCase("Channel") && part.length == 2) {
				return part[1];
			}
		}
		return null;
	}

	private void RunDisConnectionInThread() {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				int pid = getPIDof("pppd");
				String killPPPD = "";
				if (pid != -1) {
					killPPPD = "kill " + pid;
				}
				String output = Commands2script(new String[] { killPPPD,
						"sleep 1", mRFCOMM + " release " + myDigit, });
				outputInfo(output);
			}
		});
		thread.start();
	}

	private String GenerateChatFile() {
		FileWriter out = null;
		String fileName = "/data/data/" + mPackName + "/chat.txt";
		File file = new File(fileName);
		if (file.exists()) {
			file.delete();
		}
		if (D)
			Log.d(TAG, "Create");
		try {
			file.createNewFile();
			file.setExecutable(true, false);
			file.setWritable(true, false);
			file.setReadable(true, false);
			out = new FileWriter(file);
			String sAccessPoint = "";
			// String sAccessPintName = mEditAccessPoint.getText().toString();
			// if(sAccessPintName.length()>0){
			// sAccessPoint =
			// "OK              'AT+cgdcont=1,\"IP\",\""+sAccessPintName+"\"'\n";
			// }
			out.write("" + "TIMEOUT         10\n" + "ECHO            ON\n"
					+ "ABORT           '\\nABORT\\r'\n"
					+ "ABORT           '\\nERROR\\r'\n"
					+ "ABORT           '\\nNO ANSWER\\r'\n"
					+ "ABORT           '\\nNO CARRIER\\r'\n"
					+ "ABORT           '\\nNO DIALTONE\\r'\n"
					+ "ABORT           '\\nRINGING\\r\\n\\r\\nRINGING\\r'\n"
					+ "''              \\rAT\n" + "TIMEOUT         15\n"
					+ "OK              ATE1\n" + sAccessPoint
					+ "OK              ATD*99#\n" + "CONNECT");
			out.close();
			return fileName;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	private void RunConnectionInThread(final String MAC) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				outputInfo("Executing sdptool...");
				String output = Commands2script(new String[] { "sdptool browse "
						+ MAC });

				String channel = FindChannel(output);
				if (channel == null) {
					outputInfo("DUN not found");
					return;
				}
				outputInfo("Channel is " + channel + " executing rfcomm...");

				output = Commands2script(new String[] {
						mRFCOMM + " bind " + myDigit + " " + MAC + " "
								+ channel, "sleep 1", mRFCOMM, "sleep 1", });
				outputInfo(output);

				String chatFile = GenerateChatFile();
				output = Commands2script(new String[] { mPPPD + " /dev/rfcomm"
						+ myDigit + " " + "defaultroute " + "usepeerdns "
						+ "noauth " + "debug " + "unit " + myDigit + " "
						+
						// "nodetach " +
						"connect " + "\"" + mChatName + " -v -f " + chatFile
						+ "\"" });

				boolean bEndWhile = true;
				boolean bSuccess = false;
				do {

					long l = getPIDof("pppd");
					if (l == -1) {
						bEndWhile = false;
					} else {
						String IP = FindIP();
						if (IP != null) {
							bEndWhile = false;
							bSuccess = true;
							outputInfo("IP is: " + IP.substring(1));
						}
					}
					try {
						Thread.sleep(900);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} while (bEndWhile);
				if (bSuccess) {
					Commands2script(new String[] { "setprop net.dns1 8.8.8.8" });
				}
				File file = new File(chatFile);
				file.delete();
			}
		});
		thread.start();
	}

	private int getPIDof(String name) {
		String out = Commands2script(new String[] { "pidof pppd" });
		out = out.trim();
		if (out.length() > 1) {
			int l = (int) Long.parseLong(out);
			if (l > 0) {
				return l;
			}
		}
		return -1;
	}

	public static String FindIP() {

		Enumeration<NetworkInterface> interfaces = null;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		while (interfaces.hasMoreElements()) {
			NetworkInterface ifc = interfaces.nextElement();

			if (ifc.getName().equals("ppp" + myDigit)) {
				try {
					if (ifc.isUp()) {

						List<InterfaceAddress> ia = ifc.getInterfaceAddresses();
						System.out.println("IFC ip"
								+ ia.get(0).getAddress().toString());
						return ia.get(0).getAddress().toString();
					}
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return null;

	}

	private void AssetCopy(String src, String dest) {
		try {
			AssetManager assetManager = getAssets();
			InputStream in = assetManager.open(src);
			FileOutputStream out = new FileOutputStream(dest);
			int read;
			byte[] buffer = new byte[4096];
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			out.close();
			in.close();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		exec("chmod 0744 " + dest);

	}

	// Executes UNIX command.
	private String exec(String command) {
		try {
			if (D)
				Log.d(TAG, "Exec command: " + command);
			Process process = Runtime.getRuntime().exec(command);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			BufferedReader errreader = new BufferedReader(
					new InputStreamReader(process.getErrorStream()));
			int read;
			char[] buffer = new char[4096];
			StringBuffer output = new StringBuffer();
			while ((read = reader.read(buffer)) > 0) {
				output.append(buffer, 0, read);
			}
			while ((read = errreader.read(buffer)) > 0) {
				output.append(buffer, 0, read);
			}
			errreader.close();
			process.waitFor();
			if (D)
				Log.d(TAG, output.toString());

			return output.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean ExecutiveExists(String executive) {
		boolean fExists = false;
		try {
			Process process = Runtime.getRuntime().exec(executive);
			int exitCode = process.waitFor();
			fExists = exitCode != 127;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fExists;
	}

	private void outputInfo(final String str) {
		Runnable proc = new Runnable() {
			public void run() {
				outputView.setText(str);
			}
		};
		handler.post(proc);
	}

}