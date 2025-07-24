package com.example.therapyai.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.SentimentScore;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.local.models.FinalTranscriptEntry;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SentimentChartHelper {
    public static final float CHART_PADDING_FACTOR = 0.25f;
    private static final String TAG = "SentimentChartHelper";

    private static final int NUM_BARS_PER_GROUP = 3;
    private static final float GROUP_SPACE = 0.10f;
    private static final float BAR_SPACE = 0.05f;
    private static final float BAR_WIDTH = 0.25f;

    private static final float CHART_RIGHT_OFFSET_DP = 16f;

    public static void setupSentimentBarChart(BarChart chart, Context context, boolean showXLabels, boolean showYLabels, boolean showLegend) {
        if (chart == null || context == null) return;

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorOnPrimaryBackground, typedValue, true);
        @ColorInt int textColor = typedValue.data;

        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(false);
        chart.getLegend().setEnabled(showLegend);
        chart.setHighlightFullBarEnabled(false);


        // --- Interaction ---
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setScaleXEnabled(false);
        chart.setScaleYEnabled(false);
        chart.setDragXEnabled(true);

        // --- X Axis ---
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setTextColor(textColor);
        xAxis.setCenterAxisLabels(true);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setDrawLabels(showXLabels);


        // --- Left Y Axis ---
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(1f);
        leftAxis.setLabelCount(showYLabels ? 6 : 3, true);
        leftAxis.setDrawLabels(showYLabels);
        leftAxis.setDrawGridLines(showYLabels);
        leftAxis.setDrawAxisLine(showYLabels);
        leftAxis.setTextColor(textColor);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);

        // --- Right Y Axis ---
        chart.getAxisRight().setEnabled(false);

        // Improve padding to prevent edge clipping
        float density = context.getResources().getDisplayMetrics().density;
        float leftOffsetPx = 8f * density;
        float rightOffsetPx = 24f * density;
        float bottomPadding = showXLabels ? 25f * density : 5f * density;
        float topPadding = showLegend ? 25f * density : 10f * density;
        chart.setExtraOffsets(leftOffsetPx, 0f, rightOffsetPx, 0f);

        // Fix axis centering
        xAxis.setCenterAxisLabels(true);
        xAxis.setAvoidFirstLastClipping(true);

        chart.setNoDataText("Loading sentiment data...");

        chart.setViewPortOffsets(15f, topPadding, 15f, bottomPadding);

        if (showLegend) {
            chart.getLegend().setEnabled(true);
            chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
            chart.getLegend().setOrientation(Legend.LegendOrientation.HORIZONTAL);
            chart.getLegend().setDrawInside(true);
            chart.getLegend().setTextSize(10f);
            chart.getLegend().setXOffset(10f);
            chart.getLegend().setYOffset(10f);
            chart.getLegend().setWordWrapEnabled(true);
            chart.getLegend().setMaxSizePercent(0.7f);
            chart.getLegend().setTextColor(textColor);
        } else {
            chart.getLegend().setEnabled(false);
        }

        // Reduce X-axis label size to save space
        xAxis.setTextSize(10f);

        ViewGroup.LayoutParams params = chart.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            chart.setLayoutParams(params);
        }
    }

//    public static void updateSentimentBarChartData(BarChart chart, List<FinalTranscriptEntry> transcript, Context context) {
//        if (chart == null || context == null) return;
//        if (transcript == null || transcript.isEmpty()) {
//            chart.clear();
//            chart.setNoDataText("No transcript data available.");
//            chart.getXAxis().setValueFormatter(null); // Clear old formatter
//            chart.notifyDataSetChanged();
//            chart.invalidate();
//            return;
//        }
//
//        Log.d(TAG, "Updating chart data with " + transcript.size() + " transcript entries.");
//
//        ArrayList<BarEntry> positiveValues = new ArrayList<>();
//        ArrayList<BarEntry> neutralValues = new ArrayList<>();
//        ArrayList<BarEntry> negativeValues = new ArrayList<>();
//
//        TypedValue typedValue = new TypedValue();
//        context.getTheme().resolveAttribute(R.attr.positiveSentimentColor, typedValue, true);
//        int positiveColor = typedValue.data;
//        context.getTheme().resolveAttribute(R.attr.neutralSentimentColor, typedValue, true);
//        int neutralColor = typedValue.data;
//        context.getTheme().resolveAttribute(R.attr.negativeSentimentColor, typedValue, true);
//        int negativeColor = typedValue.data;
//
//
//        for (int i = 0; i < transcript.size(); i++) {
//            FinalTranscriptEntry entry = transcript.get(i);
//            SentimentScore score = entry.getSentimentScore();
//
//            // If therapist or no score, use 0f for all sentiments to make the bar "empty" or non-existent
//            // This maintains the index for chart selection to map to RecyclerView
//            if ("Therapist".equalsIgnoreCase(entry.getSpeaker()) || score == null) {
//                positiveValues.add(new BarEntry(i, 0f));
//                neutralValues.add(new BarEntry(i, 0f)); // Effectively makes therapist bars invisible if Y min is 0
//                negativeValues.add(new BarEntry(i, 0f));
//            } else { // Patient with a score
//                positiveValues.add(new BarEntry(i, score.getPositive()));
//                neutralValues.add(new BarEntry(i, score.getNeutral()));
//                negativeValues.add(new BarEntry(i, score.getNegative()));
//            }
//        }
//
//        BarDataSet positiveSet = new BarDataSet(positiveValues, "Positive");
//        positiveSet.setColor(positiveColor);
//        positiveSet.setDrawValues(false);
//
//        BarDataSet neutralSet = new BarDataSet(neutralValues, "Neutral");
//        neutralSet.setColor(neutralColor);
//        neutralSet.setDrawValues(false);
//
//        BarDataSet negativeSet = new BarDataSet(negativeValues, "Negative");
//        negativeSet.setColor(negativeColor);
//        negativeSet.setDrawValues(false);
//
//
//        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
//        dataSets.add(negativeSet); // Order can matter for stacking, but for grouping it's about labels
//        dataSets.add(neutralSet);
//        dataSets.add(positiveSet);
//
//        BarData data = new BarData(dataSets);
//        data.setBarWidth(BAR_WIDTH);
//        // data.setValueTextColor(Color.BLACK); // If values were shown
//
//        chart.setData(data);
//
//        int groupCount = transcript.size(); // Number of x-axis positions
//        if (groupCount > 0) {
//            // Configure X Axis limits and labels
//            chart.getXAxis().setAxisMinimum(-0.5f); // Start before the first group
//            chart.getXAxis().setAxisMaximum(groupCount - 1 + 0.5f); // End after the last group
//            // Center labels for grouped bars
//            chart.getXAxis().setCenterAxisLabels(true);
//
//            // Custom formatter for X-axis to show time
//            TimeAxisValueFormatter formatter = new TimeAxisValueFormatter(transcript);
//            chart.getXAxis().setValueFormatter(formatter);
//
//            // Group the bars.
//            // FromX: the starting X value (usually 0 for the first group)
//            // groupSpace: space between groups of bars
//            // barSpace: space between individual bars within a group
//            chart.groupBars(0f, GROUP_SPACE, BAR_SPACE);
//
//            // Adjust label count if needed, though often automatic is fine
//            int maxLabels = Math.min(groupCount, Math.max(5, groupCount / 4)); // Heuristic for label count
//            chart.getXAxis().setLabelCount(maxLabels, false);
//
//        } else {
//            chart.getXAxis().setAxisMinimum(0f);
//            chart.getXAxis().setAxisMaximum(0f);
//            chart.getXAxis().setValueFormatter(null);
//        }
//
//        chart.notifyDataSetChanged(); // 알림 변경
//        chart.invalidate(); // Redraw chart
//    }
    public static List<FinalTranscriptEntry> updateSentimentBarChartData(BarChart chart, List<FinalTranscriptEntry> fullTranscript, Context context) {
        if (chart == null || context == null) return new ArrayList<>();

        // Filter for patient entries with scores
        List<FinalTranscriptEntry> patientEntriesWithScores = new ArrayList<>();
        if (fullTranscript != null) {
            for (FinalTranscriptEntry entry : fullTranscript) {
                if ("Patient".equalsIgnoreCase(entry.getSpeaker()) && entry.getSentimentScore() != null) {
                    patientEntriesWithScores.add(entry);
                }
            }
        }

        if (patientEntriesWithScores.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No patient sentiment data available.");
            chart.getXAxis().setValueFormatter(null);
            chart.notifyDataSetChanged();
            chart.invalidate();
            return patientEntriesWithScores; // Return the empty filtered list
        }

        Log.d(TAG, "Updating chart data with " + patientEntriesWithScores.size() + " patient entries.");

        ArrayList<BarEntry> positiveValues = new ArrayList<>();
        ArrayList<BarEntry> neutralValues = new ArrayList<>();
        ArrayList<BarEntry> negativeValues = new ArrayList<>();

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.positiveSentimentColor, typedValue, true);
        int positiveColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.neutralSentimentColor, typedValue, true);
        int neutralColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.negativeSentimentColor, typedValue, true);
        int negativeColor = typedValue.data;

        for (int i = 0; i < patientEntriesWithScores.size(); i++) {
            FinalTranscriptEntry entry = patientEntriesWithScores.get(i);
            SentimentScore score = entry.getSentimentScore(); // Already checked for nullness
            // X value is the index in the patientEntriesWithScores list
            positiveValues.add(new BarEntry(i, score.getPositive()));
            neutralValues.add(new BarEntry(i, score.getNeutral()));
            negativeValues.add(new BarEntry(i, score.getNegative()));
        }

        BarDataSet positiveSet = new BarDataSet(positiveValues, "Positive");
        positiveSet.setColor(positiveColor);
        positiveSet.setDrawValues(false);

        BarDataSet neutralSet = new BarDataSet(neutralValues, "Neutral");
        neutralSet.setColor(neutralColor);
        neutralSet.setDrawValues(false);

        BarDataSet negativeSet = new BarDataSet(negativeValues, "Negative");
        negativeSet.setColor(negativeColor);
        negativeSet.setDrawValues(false);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(negativeSet);
        dataSets.add(neutralSet);
        dataSets.add(positiveSet);

        BarData data = new BarData(dataSets);
        data.setBarWidth(BAR_WIDTH);
        chart.setData(data);

        int groupCount = patientEntriesWithScores.size(); // Number of x-axis positions based on patient entries

        if (groupCount > 0) {
            chart.getXAxis().setAxisMinimum(-0.5f);
            chart.getXAxis().setAxisMaximum(groupCount - 1 + 0.5f);
            chart.getXAxis().setCenterAxisLabels(true);

            // Pass the filtered list to the formatter
            TimeAxisValueFormatter formatter = new TimeAxisValueFormatter(patientEntriesWithScores);
            chart.getXAxis().setValueFormatter(formatter);

            chart.groupBars(0f, GROUP_SPACE, BAR_SPACE);

            int maxLabels = Math.min(groupCount, Math.max(5, groupCount / 4));
            chart.getXAxis().setLabelCount(maxLabels, false);
        } else {
            chart.getXAxis().setAxisMinimum(0f);
            chart.getXAxis().setAxisMaximum(0f);
            chart.getXAxis().setValueFormatter(null);
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
        return patientEntriesWithScores; // Return the filtered list for mapping purposes
    }



    public static void updateProfileSessionSentimentChartData(BarChart chart, List<SessionSummary> sessionSummaries, Context context) {
        if (chart == null || context == null) return;

        if (sessionSummaries == null || sessionSummaries.isEmpty()) {
            Log.d(TAG, "Session scores data is null or empty. Clearing chart.");
            chart.clear();
            chart.setNoDataText(context.getString(R.string.chart_no_session_score_data));
            chart.getXAxis().setValueFormatter(null);
            chart.notifyDataSetChanged();
            chart.invalidate();
            return;
        }

        Log.d(TAG, "Updating profile session chart data with " + sessionSummaries.size() + " entries.");

        ArrayList<BarEntry> positiveValues = new ArrayList<>();
        ArrayList<BarEntry> neutralValues = new ArrayList<>();
        ArrayList<BarEntry> negativeValues = new ArrayList<>();

        for (int i = 0; i < sessionSummaries.size(); i++) {
            SessionSummary score = sessionSummaries.get(i);
            negativeValues.add(new BarEntry(i, (float) score.getNegative()));
            neutralValues.add(new BarEntry(i, (float) score.getNeutral()));
            positiveValues.add(new BarEntry(i, (float) score.getPositive()));
        }

        BarDataSet negativeSet = new BarDataSet(negativeValues, "Negative");
        negativeSet.setColor(Color.parseColor("#F44336")); // Red
        negativeSet.setDrawValues(false);

        BarDataSet neutralSet = new BarDataSet(neutralValues, "Neutral");
        neutralSet.setColor(Color.parseColor("#9E9E9E")); // Grey
        neutralSet.setDrawValues(false);

        BarDataSet positiveSet = new BarDataSet(positiveValues, "Positive");
        positiveSet.setColor(Color.parseColor("#4CAF50")); // Green
        positiveSet.setDrawValues(false);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(negativeSet);
        dataSets.add(neutralSet);
        dataSets.add(positiveSet);

        // --- BarData ---
        BarData data = new BarData(dataSets);
        data.setBarWidth(BAR_WIDTH);
        chart.setData(data);

        int groupCount = sessionSummaries.size();

        if (groupCount > 0) {
            chart.getXAxis().setAxisMinimum(-0.5f);
            chart.getXAxis().setAxisMaximum(groupCount - 0.5f + 0.5f);

            DateAxisValueFormatter formatter = new DateAxisValueFormatter(sessionSummaries);
            chart.getXAxis().setValueFormatter(formatter);
            chart.getXAxis().setCenterAxisLabels(true);

            chart.groupBars(0f, GROUP_SPACE, BAR_SPACE);

        }
        else {
            chart.getXAxis().setAxisMinimum(0f);
            chart.getXAxis().setAxisMaximum(0f);
            chart.getXAxis().setValueFormatter(null);
            chart.getXAxis().setLabelCount(1, true);
            chart.clear();
            chart.setNoDataText(context.getString(R.string.chart_no_session_score_data));
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    public static void applyInitialZoom(BarChart chart, float desiredVisibleGroups) {
        if (chart == null || chart.getData() == null || chart.getData().getEntryCount() == 0 || desiredVisibleGroups <= 0) {
            Log.w(TAG, "Cannot apply initial zoom: Chart/Data invalid or desiredVisibleGroups <= 0.");
            if(chart != null) {
                chart.fitScreen();
                if(chart.getData() == null || chart.getData().getEntryCount() == 0) {
                    chart.getXAxis().setAxisMinimum(0f);
                    chart.getXAxis().setAxisMaximum(0f);
                }
                chart.invalidate();
            }
            return;
        }

        int groupCount = chart.getBarData().getDataSetByIndex(0).getEntryCount();
        chart.fitScreen();

        if (groupCount <= desiredVisibleGroups) {
            chart.moveViewToX(-0.5f);
        } else {
            float scaleX = (float) groupCount / (desiredVisibleGroups - 0.75f);
            chart.zoom(scaleX, 1f, 0f, 0f);
            chart.moveViewToX(-0.5f);
        }

        chart.invalidate();
    }

    private static float parseTimeToSeconds(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) return -1f;
            String[] parts = timeStr.split(":");
            if (parts.length == 3) { // HH:mm:ss.s
                return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Float.parseFloat(parts[2]);
            } else if (parts.length == 2) { // mm:ss.s
                return Integer.parseInt(parts[0]) * 60 + Float.parseFloat(parts[1]);
            } else {
                return Float.parseFloat(timeStr);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.w(TAG, "Error parsing time string: " + timeStr, e);
            return -1f;
        }
    }

    private static String formatSecondsToMmSs(float totalSeconds) {
        if (totalSeconds < 0) return "--:--";
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private static class TimeAxisValueFormatter extends ValueFormatter {
        private final List<FinalTranscriptEntry> transcript;
        TimeAxisValueFormatter(List<FinalTranscriptEntry> transcript) {
            this.transcript = transcript != null ? transcript : new ArrayList<>();
        }
        @Override
        public String getFormattedValue(float value) {
            int index = Math.round(value);
            if (index >= 0 && index < transcript.size()) {
                FinalTranscriptEntry entry = transcript.get(index);
                String timeStr = entry.getTimestamp();
                if (timeStr != null && !timeStr.isEmpty()) {
                    float seconds = parseTimeToSeconds(timeStr);
                    if (seconds >= 0) {
                        return formatSecondsToMmSs(seconds);
                    }
                }
            }
            return "";
        }
    }

    private static class DateAxisValueFormatter extends ValueFormatter {
        private final List<SessionSummary> sessionSummaries;
        private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        private final SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd/yy", Locale.getDefault());

        DateAxisValueFormatter(List<SessionSummary> scores) {
            this.sessionSummaries = scores != null ? scores : new ArrayList<>();
        }

        @Override
        public String getFormattedValue(float value) {
            int index = Math.round(value);

            if (index >= 0 && index < sessionSummaries.size()) {
                SessionSummary score = sessionSummaries.get(index);
                String dateString = score.getDate();
                if (dateString != null && !dateString.isEmpty()) {
                    try {
                        Date date = inputFormat.parse(dateString);
                        if (date != null) {
                            return outputFormat.format(date);
                        } else {
                            Log.w(TAG, "InputFormat parsed date string '" + dateString + "' to null for index " + index);
                            return "?";
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, "ParseException for date string: '" + dateString + "' at index " + index + ". Check inputFormat pattern matches data!", e);
                        return dateString.length() >= 5 ? dateString.substring(5) : "?";
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error formatting date '" + dateString + "' at index " + index, e);
                        return "?";
                    }
                } else {
                     Log.v(TAG, "Date string is null or empty for index " + index);
                }
            } else {
                 Log.v(TAG, "Index out of bounds for DateAxisValueFormatter: " + value + " (rounded: " + index + ")");
            }
            return "";
        }
    }

    /**
     * Applies zoom to the chart so that the bars have a consistent visual width,
     * equivalent to fitting a specific number of groups (`referenceVisibleGroups`)
     * into the viewport.
     *
     * @param chart The BarChart to modify.
     * @param referenceVisibleGroups The number of groups that should define the baseline visual width.
     */
    public static void applyConsistentWidthZoom(BarChart chart, float referenceVisibleGroups) {
        if (chart == null || chart.getData() == null || chart.getData().getEntryCount() == 0 || referenceVisibleGroups <= 0) {
            Log.w(TAG, "Cannot apply consistent width zoom: Chart/Data invalid or referenceVisibleGroups <= 0.");
            if (chart != null) {
                // Reset to default state if invalid input or no data
                chart.fitScreen();
                if(chart.getData() == null || chart.getData().getEntryCount() == 0) {
                    chart.getXAxis().setAxisMinimum(0f);
                    chart.getXAxis().setAxisMaximum(0f);
                }
                chart.invalidate();
            }
            return;
        }

        // Ensure BarData width is set (assuming it was set during data creation)
        // BarData data = chart.getBarData();
        // if (data != null) {
        //     data.setBarWidth(BAR_WIDTH); // Redundant if already set, but safe
        // }

        int groupCount = chart.getBarData().getDataSetByIndex(0).getEntryCount();

        // Prevent division by zero or negative zoom
        float effectiveGroupCount = Math.max(1, groupCount);
        float targetScaleX = effectiveGroupCount / referenceVisibleGroups;

        Log.d(TAG, "applyConsistentWidthZoom: groupCount=" + groupCount +
                ", referenceVisibleGroups=" + referenceVisibleGroups +
                ", targetScaleX=" + targetScaleX);

        // 1. Reset zoom/pan state FIRST
        chart.fitScreen();

        // 2. Apply the calculated zoom factor
        // We zoom relative to the center (0,0) in this case
        chart.zoom(targetScaleX, 1f, 0f, 0f);

        // 3. Move view to the beginning of the chart data
        // The exact value might need slight adjustment depending on desired padding/centering
        // -0.5f often works well to align the first group near the left edge.
        chart.moveViewToX(-0.5f);

        // 4. Refresh the chart
        chart.invalidate();
    }
}
