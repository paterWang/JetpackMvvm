package com.example.ownzoomlib;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.Scroller;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

public class OwnZoomView extends ScrollView {
    private static final String TAG_HEADER = "header";        //头布局Tag
    private static final String TAG_ZOOM = "zoom";            //缩放布局Tag
    private static final String TAG_CONTENT = "content";      //内容布局Tag
    private boolean isZoomEnable = true;
    private float sensitive = 1.5f;
    private boolean isParallax = true;
    private int zoomTime = 500;
    private Scroller scroller;
    private int scaledTouchSlop;
    private View contentView;               //主体内容View
    private View headerView;                //头布局
    private View zoomView;
    private OnScrollListener onScrollListener;//用于缩放的View
    private OnPullZoomListener onPullZoomListener;
    private ViewGroup.LayoutParams headParams;
    private int maxTop;
    private int headerHeight;
    private float lastEventX;               //Move事件最后一次发生时的X坐标
    private float lastEventY;               //Move事件最后一次发生时的Y坐标
    private float downX;                    //Down事件的X坐标
    private float downY;                    //Down事件的Y坐标
    private boolean isZooming = false;      //是否正在被缩放
    private boolean isActionDown = false;   //第一次接收的事件是否是Down事件

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

    public void setOnPullZoomListener(OnPullZoomListener onPullZoomListener) {
        this.onPullZoomListener = onPullZoomListener;
    }

    /**
     * 滚动的监听，范围从 0 ~ maxY
     */
    public static abstract class OnScrollListener {
        public void onScroll(int l, int t, int oldl, int oldt) {
        }

        public void onHeaderScroll(int currentY, int maxY) {
        }

        public void onContentScroll(int l, int t, int oldl, int oldt) {
        }
    }

    public static abstract class OnPullZoomListener {
        public void onPullZoom(int originHeight, int currentHeight) {

        }

        public void onZoomFinish() {

        }
    }

    public OwnZoomView(Context context) {
        this(context, null);
    }

    public OwnZoomView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.scrollViewStyle);
    }

    public OwnZoomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //获取资源的数据
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.OwnZoomView);
        isZoomEnable = typedArray.getBoolean(R.styleable.OwnZoomView_ozv_isZoomEnable, isZoomEnable);
        sensitive = typedArray.getFloat(R.styleable.OwnZoomView_ozv_sensitive, sensitive);
        isParallax = typedArray.getBoolean(R.styleable.OwnZoomView_ozv_isParallax, isParallax);
        zoomTime = typedArray.getInteger(R.styleable.OwnZoomView_ozv_ZoomTime, zoomTime);
        typedArray.recycle();

        //滑动
        scroller = new Scroller(getContext());

        //获取屏幕的密度
        scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        //监听视图树变化
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnDrawListener(this::onGlobalLayout);
                maxTop = contentView.getTop();
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        findTagViews(this);
        if (headerView == null || contentView == null || zoomView == null) {
            throw new IllegalStateException("content, head , zoom 都不能为空，请在xml布局中设置tag， 或者使用属性是设置");
        }
        headParams = headerView.getLayoutParams();
        headerHeight = headParams.height;
        smoothScrollTo(0, 0);

    }

    /**
     * 递归遍历所有的View，查询Tag
     */
    private void findTagViews(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View childView = vg.getChildAt(i);
                String tag = (String) childView.getTag();
                if (tag != null) {
                    if (TAG_CONTENT.equals(tag) && contentView == null) contentView = childView;
                    if (TAG_HEADER.equals(tag) && headerView == null) headerView = childView;
                    if (TAG_ZOOM.equals(tag) && zoomView == null) zoomView = childView;
                }
                if (childView instanceof ViewGroup) {
                    findTagViews(childView);
                }
            }
        } else {
            String tag = (String) v.getTag();
            if (tag != null) {
                if (TAG_CONTENT.equals(tag) && contentView == null) contentView = v;
                if (TAG_HEADER.equals(tag) && headerView == null) headerView = v;
                if (TAG_ZOOM.equals(tag) && zoomView == null) zoomView = v;
            }
        }
    }

    private boolean scrollFlag = false;  //该标记主要是为了防止快速滑动时，onScroll回调中可能拿不到最大和最小值

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (onScrollListener != null) {
            onScrollListener.onScroll(l, t, oldl, oldt);
        }
        if (t >= 0 && t <= maxTop) {
            scrollFlag = true;
            if (onScrollListener != null) onScrollListener.onHeaderScroll(t, maxTop);
        } else if (scrollFlag) {
            scrollFlag = false;
            if (t < 0) t = 0;
            if (t > maxTop) t = maxTop;
            if (onScrollListener != null) onScrollListener.onHeaderScroll(t, maxTop);
        }

        if (t >= maxTop) {
            if (onScrollListener != null) {
                onScrollListener.onContentScroll(l, t - maxTop, oldl, oldt - maxTop);
            }
        }

        if (isParallax) {
            if (t >= 0 && t <= headerHeight) {
                headerView.scrollTo(0, -((int)(0.65 * t)));
            } else {
                headerView.scrollTo(0, 0);
            }

        }


    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float moveY = ev.getY();
                if (Math.abs(moveY - downY) > scaledTouchSlop) {
                    return true;
                }
                break;

        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isZoomEnable) return super.onTouchEvent(ev);
        float currentX = ev.getX();
        float currentY = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastEventX = currentX;
                downY = lastEventY = currentY;
                scroller.abortAnimation();
                isActionDown = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isActionDown) {
                    downX = lastEventX = currentX;
                    downY = lastEventY = currentY;
                    scroller.abortAnimation();
                    isActionDown = true;
                }
                float shiftX = Math.abs(currentX - downX);
                float shiftY = Math.abs(currentY - downY);
                float dx = currentX - lastEventX;
                float dy = currentY - lastEventY;

                lastEventY = currentY;
                if (isTop()) {
                    if (shiftY > shiftX && shiftY > scaledTouchSlop) {
                        int height =( headParams.height) + ((int) (dy / sensitive + 0.5));
                        if (height <= headerHeight) {
                            height = headerHeight;
                            isZooming = false;

                        } else {
                            isZooming = true;
                        }

                        headParams.height = height;
                        headerView.setLayoutParams(headParams);
                        if (onPullZoomListener != null)
                            onPullZoomListener.onPullZoom(headerHeight, headParams.height);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isActionDown = false;
                if (isZooming) {
                    scroller.startScroll(0, headParams.height, 0, -(headParams.height - headerHeight), zoomTime);
                    isZooming = false;
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                break;
        }

        return isZooming || super.onTouchEvent(ev);
    }

    private boolean isStartScroll = false;          //当前是否下拉过

    @Override
    public void computeScroll() {
        super.computeScroll();

        if (scroller.computeScrollOffset()) {
            isStartScroll = true;
            headParams.height = scroller.getCurrY();
            headerView.setLayoutParams(headParams);
            if (onPullZoomListener != null)
                onPullZoomListener.onPullZoom(headerHeight, headParams.height);
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            if (onPullZoomListener != null && isStartScroll) {
                isStartScroll = false;
                onPullZoomListener.onZoomFinish();

            }
        }

    }

    public boolean isTop() {
        return getScrollY() <= 0;
    }

    public void setSensitive(float sensitive) {
        this.sensitive = sensitive;
    }

    public void setZoomEnable(boolean zoomEnable) {
        isZoomEnable = zoomEnable;
    }

    public void setZoomTime(int zoomTime) {
        this.zoomTime = zoomTime;
    }

    public void setParallax(boolean parallax) {
        isParallax = parallax;
    }
}
