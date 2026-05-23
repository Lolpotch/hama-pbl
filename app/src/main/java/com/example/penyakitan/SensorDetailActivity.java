package com.example.penyakitan;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SensorDetailActivity extends AppCompatActivity {

    private TextView tvSensorDetailTitle, tvSensorDetailSubtitle;
    private TextView tvLatestSensorLabel, tvLatestSensorValue, tvLatestSensorTime;
    private TextView tvSensorChartTitle, tvSensorTableTitle;
    private ImageView btnBackSensorDetail;
    private LineChart sensorLineChart;
    private TableLayout tableSensorHistory;

    private DatabaseReference sensorLatestRef;
    private DatabaseReference sensorHistoryRef;

    private String sensorType = "temperature";
    private String dataKey = "temperature";
    private String title = "Suhu Udara";
    private String unit = "°C";
    private int chartColor = Color.parseColor("#FF6B1A");
    private static final String DATABASE_URL =
            "https://lokasighthama-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);

        readSensorType();
        initView();
        initFirebase();
        setupHeader();
        loadLatestSensorData();
        loadSensorHistory();
    }

    private void readSensorType() {
        String requestedType = getIntent().getStringExtra("sensor_type");

        if (requestedType != null && requestedType.equalsIgnoreCase("humidity")) {
            sensorType = "humidity";
            dataKey = "humidity";
            title = "Kelembapan";
            unit = "%";
            chartColor = Color.parseColor("#1E88E5");
        }
    }

    private void initView() {
        btnBackSensorDetail = findViewById(R.id.btnBackSensorDetail);
        tvSensorDetailTitle = findViewById(R.id.tvSensorDetailTitle);
        tvSensorDetailSubtitle = findViewById(R.id.tvSensorDetailSubtitle);
        tvLatestSensorLabel = findViewById(R.id.tvLatestSensorLabel);
        tvLatestSensorValue = findViewById(R.id.tvLatestSensorValue);
        tvLatestSensorTime = findViewById(R.id.tvLatestSensorTime);
        tvSensorChartTitle = findViewById(R.id.tvSensorChartTitle);
        tvSensorTableTitle = findViewById(R.id.tvSensorTableTitle);
        sensorLineChart = findViewById(R.id.sensorLineChart);
        tableSensorHistory = findViewById(R.id.tableSensorHistory);

        if (btnBackSensorDetail != null) {
            btnBackSensorDetail.setOnClickListener(v -> finish());
        }
    }

    private void initFirebase() {
        sensorLatestRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("sensor")
                .child("dht22")
                .child("latest");

        sensorHistoryRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("sensor")
                .child("dht22")
                .child("history");
    }

    private void setupHeader() {
        tvSensorDetailTitle.setText("Detail " + title);
        tvSensorDetailSubtitle.setText("Data terbaru, grafik, dan tabel riwayat " + title.toLowerCase(Locale.US));
        tvLatestSensorLabel.setText(title + " Terbaru");
        tvSensorChartTitle.setText("Grafik " + title + " 10 Data Terakhir");
        tvSensorTableTitle.setText("Tabel " + title + " 20 Data Terakhir");
    }

    private void loadLatestSensorData() {
        sensorLatestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double value = snapshot.child(dataKey).getValue(Double.class);
                String time = getSensorTime(snapshot);

                if (value == null) {
                    tvLatestSensorValue.setText("--");
                } else {
                    tvLatestSensorValue.setText(formatNumber(value) + unit);
                }

                tvLatestSensorTime.setText("Update: " + time);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvLatestSensorValue.setText("--");
                tvLatestSensorTime.setText("Update: gagal memuat data");
            }
        });
    }

    private void loadSensorHistory() {
        Query query = sensorHistoryRef.limitToLast(20);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SensorRecord> records = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    Double value = data.child(dataKey).getValue(Double.class);

                    if (value == null) {
                        continue;
                    }

                    records.add(new SensorRecord(value, getSensorTime(data)));
                }

                renderChart(records);
                renderTable(records);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                renderChart(new ArrayList<>());
                renderTable(new ArrayList<>());
            }
        });
    }

    private void renderChart(List<SensorRecord> records) {
        List<Entry> entries = new ArrayList<>();
        int start = Math.max(0, records.size() - 10);
        int index = 0;

        for (int i = start; i < records.size(); i++) {
            entries.add(new Entry(index, records.get(i).value.floatValue()));
            index++;
        }

        LineDataSet dataSet = new LineDataSet(entries, title + " (" + unit + ")");
        dataSet.setColor(chartColor);
        dataSet.setCircleColor(chartColor);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        sensorLineChart.setData(new LineData(dataSet));
        sensorLineChart.getDescription().setEnabled(false);
        sensorLineChart.getLegend().setEnabled(true);
        sensorLineChart.setTouchEnabled(true);
        sensorLineChart.setDragEnabled(true);
        sensorLineChart.setScaleEnabled(false);
        sensorLineChart.setPinchZoom(false);

        XAxis xAxis = sensorLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#667085"));
        xAxis.setGridColor(Color.parseColor("#EEEEEE"));
        xAxis.setGranularity(1f);

        sensorLineChart.invalidate();
    }

    private void renderTable(List<SensorRecord> records) {
        tableSensorHistory.removeAllViews();
        addTableRow("Waktu", title, true);

        List<SensorRecord> newestFirst = new ArrayList<>(records);
        Collections.reverse(newestFirst);

        if (newestFirst.isEmpty()) {
            addTableRow("-", "Belum ada data", false);
            return;
        }

        for (SensorRecord record : newestFirst) {
            addTableRow(record.time, formatNumber(record.value) + unit, false);
        }
    }

    private void addTableRow(String time, String value, boolean header) {
        TableRow row = new TableRow(this);
        row.setPadding(0, dpToPx(6), 0, dpToPx(6));

        TextView timeView = createTableCell(time, header);
        TextView valueView = createTableCell(value, header);
        valueView.setGravity(Gravity.END);

        row.addView(timeView);
        row.addView(valueView);
        tableSensorHistory.addView(row);
    }

    private TextView createTableCell(String text, boolean header) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(header ? 13 : 12);
        view.setTextColor(header ? Color.parseColor("#2F6B2A") : Color.parseColor("#475467"));
        view.setTypeface(null, header ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        view.setPadding(0, dpToPx(4), dpToPx(10), dpToPx(4));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private String getSensorTime(DataSnapshot snapshot) {
        String time = getSafeString(snapshot.child("time"));

        if (time == null || time.trim().isEmpty()) {
            time = getSafeString(snapshot.child("timestamp"));
        }

        if (time == null || time.trim().isEmpty()) {
            time = getSafeString(snapshot.child("created_at"));
        }

        if (time == null || time.trim().isEmpty()) {
            time = "-";
        }

        return time;
    }

    private String getSafeString(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return "";
        }

        Object value = snapshot.getValue();

        if (value == null) {
            return "";
        }

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object summary = map.get("summary");

            if (summary != null) {
                return String.valueOf(summary);
            }

            return "";
        }

        return String.valueOf(value);
    }

    private String formatNumber(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }

        return String.format(Locale.US, "%.1f", value);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class SensorRecord {
        Double value;
        String time;

        SensorRecord(Double value, String time) {
            this.value = value;
            this.time = time;
        }
    }
}
