package androidx.constraintlayout.experiments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.motion.widget.TransitionAdapter;

import java.text.DecimalFormat;
import java.util.Vector;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

public class GraphView extends View {
    private static final String TAG = "GraphView";
    Context context;
    Margins mMargins = new Margins();
    Vector<DrawItem> myDrawItems = new Vector<DrawItem>();
    Plot plot = new Plot();
    Ticks mTicks = new Ticks();
    float min_y = 0;
    float max_y = 1;

    static class Margins {
        int myInsTop = 30;
        int myInsLeft = 200;
        int myInsBottom = 30;
        int myInsRight = 30;
    }

    public GraphView(Context context) {
        super(context);
        init();
    }

    public GraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        myDrawItems.add(mAxis);
        myDrawItems.add(plot);

        //    myDrawItems.add(mTicks);
    }

    public void resetPlot() {
        plot.reset();
        max_y = 1;
        min_y = 0;
        invalidate();
    }

    public void resetVMode() {
        plot.reset();
        min_y = -1;
        plot.velocityGraph = true;
        invalidate();
    }

    public void graphTouch(boolean up) {
        plot.touch(up);
        ;
    }

    public void addPoint(float pos) {
        plot.addTimePoint(pos);
        invalidate();
    }

    public void addPoint(float pos, float vel) {
        plot.addTimePoint(pos, vel);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        mTicks.calcRangeTicks(w, h);

        for (DrawItem drawItem : myDrawItems) {
            drawItem.paint(canvas, mMargins, w, h);
        }
    }

    interface DrawItem {
        public boolean paint(Canvas c, Margins m, int w, int h);
    }

    class Plot implements DrawItem {
        Path path = new Path();
        Paint paint = new Paint();
        Paint vpaint = new Paint();

        float[] pos = new float[1000];
        float[] time = new float[pos.length];
        float[] velocity = new float[pos.length];
        boolean[] touch = new boolean[pos.length];
        int number_of_points = 0;
        boolean velocityGraph = false;
        float timeWindow = Float.NaN; // NaN is dynamic
        long start;
        boolean touchUp = false;

        public void reset() {
            number_of_points = 0;
            start = System.nanoTime();
            timeWindow = 2;
            touchUp = false;
        }

        public void touch(boolean up) {
            touchUp = up;
        }

        public void setTimeWindow(float time_in_seconds) {
            timeWindow = time_in_seconds;
        }

        public void addPoint(float x, float y) {
            pos[number_of_points] = y;
            time[number_of_points] = x;
            touch[number_of_points] = touchUp;
            number_of_points++;
        }

        public void addPoint(float x, float y, float v) {
            velocity[number_of_points] = v;
            pos[number_of_points] = y;
            time[number_of_points] = x;
            touch[number_of_points] = touchUp;
            number_of_points++;
        }

        public void addTimePoint(float y, float vel) {
            long now = System.nanoTime() - start;
            addPoint(now * 1E-9f, y, vel);
        }

        public void addTimePoint(float y) {
            long now = System.nanoTime() - start;
            addPoint(now * 1E-9f, y);
        }

        {
            number_of_points = 100;
            for (int i = 0; i < number_of_points; i++) {
                float y = i / (float) (number_of_points);
                pos[i] = y * y;
                time[i] = i;
            }


            paint.setStrokeWidth(4);
            paint.setColor(0xFF0000FF);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);

            vpaint.setStrokeWidth(4);
            vpaint.setColor(0xFF0000FF);
            vpaint.setStrokeJoin(Paint.Join.ROUND);
            vpaint.setStrokeCap(Paint.Cap.ROUND);
            vpaint.setStyle(Paint.Style.STROKE);
            vpaint.setPathEffect(new DashPathEffect(new float[] {20f,20f}, 0f));

        }

        private void paintV(Canvas c, Margins m, int w, int h) {
            int cw = w - m.myInsLeft - m.myInsRight;
            int ch = h - m.myInsTop - m.myInsBottom;
            float maxTime = time[number_of_points - 1];
            float offx = m.myInsLeft;
            float scalex = cw / (float) maxTime;
            boolean windowed = false;
            if (!Float.isNaN(timeWindow)) {
                scalex = cw / timeWindow;
                if (timeWindow < maxTime) {
                    offx = m.myInsLeft - scalex * (maxTime - timeWindow);
                    windowed = true;
                }
            }
            float minv = velocity[0], maxv = velocity[0];
            for (int i = 0; i < number_of_points; i++) {
                minv = Math.min(minv, velocity[i]);
                maxv = Math.max(maxv, velocity[i]);
            }

            min_y = Math.min(min_y + 0.1f * (minv - min_y), 0);
            max_y = Math.max(max_y + 0.1f * (maxv - max_y), 1);
             float scaley = -ch / (max_y - min_y);


            float offy = h + -m.myInsBottom + min_y * ch / (max_y - min_y);
            path.reset();
            float x = 0, y = 0;
            for (int i = 0; i < number_of_points; i++) {
                y = velocity[i] * scaley + offy;
                x = time[i] * scalex + offx;
                if (i == 0) {
                    vpaint.setColor(touch[i] ? 0xFF555599 : 0xFF998866);
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                    if (touch[i] != touch[i - 1]) {

                        vpaint.setStyle(Paint.Style.STROKE);
                        c.drawPath(path, vpaint);
                        vpaint.setStyle(Paint.Style.FILL);
                        int s = 10;
                        c.drawRoundRect(x - s, y - s, x + s, y + s, s * 2, s * 2, vpaint);
                        path.reset();
                        path.moveTo(x, y);
                        vpaint.setColor(touch[i] ? 0xFF555599 : 0xFF998866);
                    }
                }
            }
            vpaint.setStyle(Paint.Style.STROKE);
            c.drawPath(path, vpaint);
            vpaint.setStyle(Paint.Style.FILL);
            int s = 10;
            c.drawRoundRect(x - s, y - s, x + s, y + s, s * 2, s * 2, vpaint);
        }


        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean paint(Canvas c, Margins m, int w, int h) {
            Log.v(TAG, " paint ");
            if (number_of_points == 0) {
                return false;
            }
            if (velocityGraph) {
                paintV(c, m, w, h);
            }
            int cw = w - m.myInsLeft - m.myInsRight;
            int ch = h - m.myInsTop - m.myInsBottom;
            float maxTime = time[number_of_points - 1];
            float offx = m.myInsLeft;
            float scalex = cw / (float) maxTime;
            boolean windowed = false;
            if (!Float.isNaN(timeWindow)) {
                scalex = cw / timeWindow;
                if (timeWindow < maxTime) {
                    offx = m.myInsLeft - scalex * (maxTime - timeWindow);
                    windowed = true;
                }
            }
            float scaley, offy;
            if (velocityGraph){

                  scaley = -ch / (max_y - min_y);


                  offy = h + -m.myInsBottom + min_y * ch / (max_y - min_y);
            } else {
                  scaley = -ch;
                  offy = h - m.myInsBottom;
            }
            path.reset();
            float x = 0, y = 0;
            for (int i = 0; i < number_of_points; i++) {
                y = pos[i] * scaley + offy;
                x = time[i] * scalex + offx;
                if (i == 0) {
                    paint.setColor(touch[i] ? 0xFF0000FF : 0xFFF88800);
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                    if (touch[i] != touch[i - 1]) {

                        paint.setStyle(Paint.Style.STROKE);
                        c.drawPath(path, paint);
                        paint.setStyle(Paint.Style.FILL);
                        int s = 10;
                        c.drawRoundRect(x - s, y - s, x + s, y + s, s * 2, s * 2, paint);
                        path.reset();
                        path.moveTo(x, y);
                        paint.setColor(touch[i] ? 0xFF0000FF : 0xFFF88800);
                    }
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            int s = 10;
            c.drawRoundRect(x - s, y - s, x + s, y + s, s * 2, s * 2, paint);
            return windowed;
        }
    }

    ;
    DrawItem mAxis = new DrawItem() {
        DecimalFormat df = new DecimalFormat("##.0");
        Paint mAxisPaint = new Paint();

        {
            mAxisPaint.setTextSize(48);
            mAxisPaint.setStrokeWidth(4);
        }

        @Override
        public boolean paint(Canvas c, Margins m, int w, int h) {

            c.drawLine(m.myInsLeft, m.myInsTop, m.myInsLeft, h - m.myInsBottom, mAxisPaint);
            int ypos = h - m.myInsBottom;
            if (min_y != 0.0f) {
                ypos = (int) (h - m.myInsBottom - (h - m.myInsBottom - m.myInsTop) * (0 - min_y) / (max_y - min_y));
            }
           float mt;
            String s;
            c.drawLine(m.myInsLeft, ypos, w - m.myInsRight, ypos, mAxisPaint);
            s = df.format(max_y);
            mt = 8+ mAxisPaint.measureText(s);

            c.drawText(s, m.myInsLeft - mt, m.myInsTop + 10, mAxisPaint);
            s = df.format(min_y);
            mt = 8+ mAxisPaint.measureText(s);

            c.drawText(s, m.myInsLeft - mt, h - m.myInsBottom, mAxisPaint);
            mt = 8+ mAxisPaint.measureText("position");
            c.drawText("position", m.myInsLeft-mt, (h - m.myInsBottom - m.myInsTop) / 2 + m.myInsTop, mAxisPaint);
            return false;
        }
    };

    static class Ticks implements DrawItem {
        float myActualMinx, myActualMiny, myActualMaxx, myActualMaxy;
        float myLastMinx, myLastMiny, myLastMaxx, myLastMaxy;
        float myMinx, myMiny, myMaxx, myMaxy;
        float myTickX;
        float myTickY;
        int myTextGap = 2;

        void calcRangeTicks(int width, int height) {
            double dx = myActualMaxx - myActualMinx;
            double dy = myActualMaxy - myActualMiny;
            int sw = width;
            int sh = height;

            double border = 1.09345;

            if (Math.abs(myLastMinx - myActualMinx)
                    + Math.abs(myLastMaxx - myActualMaxx) > 0.1 * (myActualMaxx - myActualMinx)) {
                myTickX = (float) calcTick(sw, dx);
                dx = myTickX * Math.ceil(border * dx / myTickX);
                double tx = (myActualMinx + myActualMaxx - dx) / 2;
                tx = myTickX * Math.floor(tx / myTickX);
                myMinx = (float) tx;
                tx = (myActualMinx + myActualMaxx + dx) / 2;
                tx = myTickX * Math.ceil(tx / myTickX);
                myMaxx = (float) tx;

                myLastMinx = myActualMinx;
                myLastMaxx = myActualMaxx;
            }
            if (Math.abs(myLastMiny - myActualMiny)
                    + Math.abs(myLastMaxy - myActualMaxy) > 0.1 * (myActualMaxy - myActualMiny)) {
                myTickY = (float) calcTick(sh, dy);
                dy = myTickY * Math.ceil(border * dy / myTickY);
                double ty = (myActualMiny + myActualMaxy - dy) / 2;
                ty = myTickY * Math.floor(ty / myTickY);
                myMiny = (float) ty;
                ty = (myActualMiny + myActualMaxy + dy) / 2;
                ty = myTickY * Math.ceil(ty / myTickY);
                myMaxy = (float) ty;

                myLastMiny = myActualMiny;
                myLastMaxy = myActualMaxy;
            }

            // TODO: cleanup
            myMinx = 0;
            myMiny = 0;
            myMaxx = 1;
            myMaxy = 1;
        }

        public void rangeReset() {
            myActualMinx = Float.MAX_VALUE;
            myActualMiny = Float.MAX_VALUE;
            myActualMaxx = -Float.MAX_VALUE;
            myActualMaxy = -Float.MAX_VALUE;
        }

        public void rangeIncludePoint(float x, float y) {
            myActualMinx = Math.min(myActualMinx, x);
            myActualMiny = Math.min(myActualMiny, y);
            myActualMaxx = Math.max(myActualMaxx, x);
            myActualMaxy = Math.max(myActualMaxy, y);
        }

        static private double calcTick(int scr, double range) {
            int aprox_x_ticks = scr / 50;
            int type = 1;
            double best = Math.log10(range / ((double) aprox_x_ticks));
            double n = Math.log10(range / ((double) aprox_x_ticks * 2));
            if (frac(n) < frac(best)) {
                best = n;
                type = 2;
            }
            n = Math.log10(range / (aprox_x_ticks * 5));
            if (frac(n) < frac(best)) {
                best = n;
                type = 5;
            }
            return type * Math.pow(10, Math.floor(best));
        }

        static private double frac(double x) {
            return x - Math.floor(x);
        }

        DecimalFormat df = new DecimalFormat("###.#");
        Paint mPaint = new Paint();
        Rect bounds = new Rect();

        @Override
        public boolean paint(Canvas c, Margins m, int w, int h) {


            int draw_width = w - m.myInsLeft - m.myInsRight;
            float e = 0.0001f * (myMaxx - myMinx);
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float ascent = fm.ascent;

            for (float i = myMinx; i <= myMaxx + e; i += myTickX) {
                int ix = (int) (draw_width * (i - myMinx) / (myMaxx - myMinx) + m.myInsLeft);
                c.drawLine(ix, m.myInsTop, ix, h - m.myInsBottom, mPaint);
                String str = df.format(i);
                mPaint.getTextBounds(str, 0, str.length(), bounds);
                int sw = bounds.width();

                c.drawText(str, ix - sw, h - m.myInsBottom + ascent + myTextGap, mPaint);
            }
            int draw_height = h - m.myInsTop - m.myInsLeft;
            e = 0.0001f * (myMaxy - myMiny);
            float hightoff = -fm.descent + fm.ascent / 2 + ascent;
            for (float i = myMiny; i <= myMaxy + e; i += myTickY) {
                int iy = (int) (draw_height * (1 - (i - myMiny) / (myMaxy - myMiny)) + m.myInsTop);
                c.drawLine(m.myInsLeft, iy, w - m.myInsRight, iy, mPaint);
                String str = df.format(i);
                mPaint.getTextBounds(str, 0, str.length(), bounds);
                int sw = bounds.width();


                c.drawText(str, m.myInsLeft - sw - myTextGap, iy + hightoff, mPaint);

            }
            return false;
        }


    }
    @SuppressLint("ClickableViewAccessibility")
    void atachTo(MotionLayout motionLayout) {

        if (motionLayout == null) {
            return;
        }
        motionLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == ACTION_UP) {
                graphTouch(true);
            }
            if (event.getAction() == ACTION_DOWN) {
                graphTouch(false);
            }
            return false;
        });
        motionLayout.addTransitionListener(new TransitionAdapter() {
            boolean velMode = false;
            boolean multiStage = false;
            int mStartID;
            int mEndID;
            int count = -1;

            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
                boolean reset = (startId == mStartID && endId == mEndID);

                mStartID = startId;
                mEndID = endId;
                velMode = true;
                multiStage = "multi".equals(getTag());
                count = (count+1)%5;
              if (count != 0)   {
                  return;
              }
                if (velMode) {
                    if (reset)
                        resetVMode();
                } else {
                    if (multiStage) {
                        if (reset) {
                            resetPlot();
                        }
                    } else {
                        resetPlot();
                    }

                }
            }

            @Override
            public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {

                if (velMode) {
                    addPoint(progress, motionLayout.getVelocity());
                } else {
                    addPoint(progress);
                }
            }

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {

            }
        });
    }
}
