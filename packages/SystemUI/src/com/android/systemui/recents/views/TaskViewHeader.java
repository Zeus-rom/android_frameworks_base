/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.recents.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.PorterDuff.Mode;
import android.provider.Settings;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.recents.Constants;
import com.android.systemui.recents.RecentsConfiguration;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.misc.Utilities;
import com.android.systemui.recents.model.RecentsTaskLoader;
import com.android.systemui.recents.model.Task;


/* The task bar view */
public class TaskViewHeader extends FrameLayout {

    RecentsConfiguration mConfig;
    private SystemServicesProxy mSsp;

    // Header views
    ImageView mMoveTaskButton;
    ImageView mDismissButton;
    ImageView mPinButton;
    ImageView mFloatButton;
    ImageView mApplicationIcon;
    TextView mActivityDescription;

    // Header drawables
    boolean mCurrentPrimaryColorIsDark;
    int mCurrentPrimaryColor;
    int mBackgroundColor;
    Drawable mLightDismissDrawable;
    Drawable mDarkDismissDrawable;
    Drawable mLightPinDrawable;
    Drawable mDarkPinDrawable;
    Drawable mLightFloatDrawable;
    Drawable mDarkFloatDrawable;
    RippleDrawable mBackground;
    GradientDrawable mBackgroundColorDrawable;
    AnimatorSet mFocusAnimator;
    String mDismissContentDescription;
    int mTaskbarIconLightColor;
    int mTaskbarIconDarkColor;

    // Static highlight that we draw at the top of each view
    static Paint sHighlightPaint;

    // Header dim, which is only used when task view hardware layers are not used
    Paint mDimLayerPaint = new Paint();
    PorterDuffColorFilter mDimColorFilter = new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_ATOP);

    boolean mLayersDisabled;

    public TaskViewHeader(Context context) {
        this(context, null);
    }

    public TaskViewHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskViewHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskViewHeader(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mConfig = RecentsConfiguration.getInstance();
        mSsp = RecentsTaskLoader.getInstance().getSystemServicesProxy();
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            }
        });

        // Load the dismiss resources
        Resources res = context.getResources();
        mLightDismissDrawable = res.getDrawable(R.drawable.recents_dismiss_light);
        mDarkDismissDrawable = res.getDrawable(R.drawable.recents_dismiss_dark);
        mDismissContentDescription =
                res.getString(R.string.accessibility_recents_item_will_be_dismissed);

        // Load the screen pinning resources
        mLightPinDrawable = res.getDrawable(R.drawable.ic_pin);
        mDarkPinDrawable = res.getDrawable(R.drawable.ic_pin_dark);

        // Load floating windows intent
        mLightFloatDrawable = context.getDrawable(R.drawable.ic_floating_on);
        mDarkFloatDrawable = context.getDrawable(R.drawable.ic_qs_dark_floating_on);

        // Configure the highlight paint
        if (sHighlightPaint == null) {
            sHighlightPaint = new Paint();
            sHighlightPaint.setStyle(Paint.Style.STROKE);
            sHighlightPaint.setStrokeWidth(mConfig.taskViewHighlightPx);
            sHighlightPaint.setColor(mConfig.taskBarViewHighlightColor);
            sHighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            sHighlightPaint.setAntiAlias(true);
        }

        mTaskbarIconLightColor = res.getColor(R.color.recents_task_bar_light_dismiss_color);
        mTaskbarIconDarkColor = res.getColor(R.color.recents_task_bar_dark_dismiss_color);
    }

    @Override
    protected void onFinishInflate() {
        // Initialize the icon and description views
        mApplicationIcon = (ImageView) findViewById(R.id.application_icon);
        mActivityDescription = (TextView) findViewById(R.id.activity_description);
        mDismissButton = (ImageView) findViewById(R.id.dismiss_task);
        mMoveTaskButton = (ImageView) findViewById(R.id.move_task);
        mPinButton = (ImageView) findViewById(R.id.lock_to_app_fab);
		mFloatButton = (ImageView) findViewById(R.id.float_task);

        // Hide the backgrounds if they are ripple drawables
        if (!Constants.DebugFlags.App.EnableTaskFiltering) {
            if (mApplicationIcon.getBackground() instanceof RippleDrawable) {
                mApplicationIcon.setBackground(null);
            }
        }

        mBackgroundColorDrawable = (GradientDrawable) getContext().getDrawable(R.drawable
                .recents_task_view_header_bg_color);
        // Copy the ripple drawable since we are going to be manipulating it
        mBackground = (RippleDrawable)
                getContext().getDrawable(R.drawable.recents_task_view_header_bg);
        mBackground = (RippleDrawable) mBackground.mutate().getConstantState().newDrawable();
        mBackground.setColor(ColorStateList.valueOf(0));
        mBackground.setDrawableByLayerId(mBackground.getId(0), mBackgroundColorDrawable);
        setBackground(mBackground);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the highlight at the top edge (but put the bottom edge just out of view)
        float offset = (float) Math.ceil(mConfig.taskViewHighlightPx / 2f);
        float radius = mConfig.taskViewRoundedCornerRadiusPx;
        int count = canvas.save(Canvas.CLIP_SAVE_FLAG);
        canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(-offset, 0f, (float) getMeasuredWidth() + offset,
                getMeasuredHeight() + radius, radius, radius, sHighlightPaint);
        canvas.restoreToCount(count);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Sets the dim alpha, only used when we are not using hardware layers.
     * (see RecentsConfiguration.useHardwareLayers)
     */
    void setDimAlpha(int alpha) {
        mDimColorFilter.setColor(Color.argb(alpha, 0, 0, 0));
        mDimLayerPaint.setColorFilter(mDimColorFilter);
        if (!mLayersDisabled) {
            setLayerType(LAYER_TYPE_HARDWARE, mDimLayerPaint);
        }
    }

    /** Returns the secondary color for a primary color. */
    int getSecondaryColor(int primaryColor, boolean useLightOverlayColor) {
        int overlayColor = useLightOverlayColor ? Color.WHITE : Color.BLACK;
        return Utilities.getColorWithOverlay(primaryColor, overlayColor, 0.8f);
    }

    /** Binds the bar view to the task */
    public void rebindToTask(Task t) {
        // If an activity icon is defined, then we use that as the primary icon to show in the bar,
        // otherwise, we fall back to the application icon
        if (t.activityIcon != null) {
            mApplicationIcon.setImageDrawable(t.activityIcon);
        } else if (t.applicationIcon != null) {
            mApplicationIcon.setImageDrawable(t.applicationIcon);
        }
        if (!mActivityDescription.getText().toString().equals(t.activityLabel)) {
            mActivityDescription.setText(t.activityLabel);
        }
        mActivityDescription.setContentDescription(t.contentDescription);

        // Try and apply the system ui tint
        int existingBgColor = (getBackground() instanceof ColorDrawable) ?
                ((ColorDrawable) getBackground()).getColor() : 0;
        if (existingBgColor != t.colorPrimary) {
            mBackgroundColorDrawable.setColor(t.colorPrimary);
            mBackgroundColor = t.colorPrimary;
        }
        mCurrentPrimaryColor = t.colorPrimary;
        mCurrentPrimaryColorIsDark = t.useLightOnPrimaryColor;
        mActivityDescription.setTextColor(t.useLightOnPrimaryColor ?
                mConfig.taskBarViewLightTextColor : mConfig.taskBarViewDarkTextColor);
        mPinButton.setImageDrawable(t.useLightOnPrimaryColor ?
                mLightPinDrawable : mDarkPinDrawable);
        mDismissButton.setImageDrawable(t.useLightOnPrimaryColor ?
                mLightDismissDrawable : mDarkDismissDrawable);
        mDismissButton.setContentDescription(String.format(mDismissContentDescription,
                t.contentDescription));
        boolean floatingswitch = Settings.System.getInt(mContext.getContentResolver(), Settings.System.FLOATING_WINDOW_MODE, 0) == 1;
		mFloatButton.setImageDrawable(t.useLightOnPrimaryColor ?
                mLightFloatDrawable : mDarkFloatDrawable);
        updaterecentstyles(t);        
        if (mConfig.multiStackEnabled) {
            updateResizeTaskBarIcon(t);
        }
    }

    public void updaterecentstyles(Task t) {
        boolean mClearStyleSwitch  = Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.CLEAR_RECENTS_STYLE_ENABLE, 0) == 1;	
	final Resources res = getContext().getResources();
	int mTint = 0x00ffffff;		
	int Black = Color.BLACK;
	int mLightColor = res.getColor(R.color.recents_task_bar_dark_dismiss_color);
	int mDarkColor = res.getColor(R.color.recents_task_bar_dark_dismiss_color);
	mApplicationIcon = (ImageView) findViewById(R.id.application_icon);				 
	int mFloatcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FLOAT_BUTTON_COLOR, 0xffDC4C3C);	
	int mPincolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIN_BUTTON_COLOR, 0xff009688);	
	int mMwcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MW_BUTTON_COLOR, 0xFFFFFFFF);
	int mKillColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.KILL_APP_BUTTON_COLOR, 0xFFFFFFFF);
	int mAppColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TV_APP_COLOR, 0x00000000);
        int mTextColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TV_APP_TEXT_COLOR, 0xFFFFFFFF);       
	if (mClearStyleSwitch) {
		if (mDismissButton != null) {
			 mDismissButton.setColorFilter(mKillColor,Mode.SRC_ATOP);
		}
		if(mPinButton !=null) {
			  mPinButton.setColorFilter(mPincolor,Mode.SRC_ATOP);
		}      	
		if(mMoveTaskButton !=null) {
			  mMoveTaskButton.setColorFilter(mMwcolor,Mode.SRC_ATOP);
		}
		if (mFloatButton !=null) {
			  mFloatButton.setColorFilter(mFloatcolor,Mode.SRC_ATOP);
		}
		if(mApplicationIcon !=null) {
			  if (mAppColor != mTint) {
			  mApplicationIcon.setColorFilter(mAppColor);
			  }
		}
		if(mActivityDescription !=null) {
			 mActivityDescription.setTextColor(mTextColor);
		}
        } else {
		if (mDismissButton != null) {
			 mDismissButton.setColorFilter(null);
		}
		if(mPinButton !=null) {
			  mPinButton.setColorFilter(null);
		}      	
		if(mMoveTaskButton !=null) {
			  mMoveTaskButton.setColorFilter(null);
		}
		if (mFloatButton !=null) {
		mFloatButton = (ImageView) findViewById(R.id.float_task);
		mFloatButton.setImageDrawable(t.useLightOnPrimaryColor ?
                mLightFloatDrawable : mDarkFloatDrawable);
                mFloatButton.setColorFilter(null);
			if(mDarkFloatDrawable!=null) {
			mDarkFloatDrawable.setColorFilter(mDarkColor,Mode.SRC_IN);
			}
		}
		if(mApplicationIcon !=null) {
			  if (mAppColor != mTint) {
			  mApplicationIcon.setColorFilter(null);
			  }
		}
		if(mActivityDescription !=null) {
		        mActivityDescription.setTextColor(t.useLightOnPrimaryColor ?
                mConfig.taskBarViewLightTextColor : mConfig.taskBarViewDarkTextColor);
		}
        }
    
    }

    /** Updates the resize task bar button. */
    void updateResizeTaskBarIcon(Task t) {
        Rect display = mSsp.getWindowRect();
        Rect taskRect = mSsp.getTaskBounds(t.key.stackId);
        int resId = R.drawable.star;
        if (display.equals(taskRect) || taskRect.isEmpty()) {
            resId = R.drawable.vector_drawable_place_fullscreen;
        } else {
            boolean top = display.top == taskRect.top;
            boolean bottom = display.bottom == taskRect.bottom;
            boolean left = display.left == taskRect.left;
            boolean right = display.right == taskRect.right;
            if (top && bottom && left) {
                resId = R.drawable.vector_drawable_place_left;
            } else if (top && bottom && right) {
                resId = R.drawable.vector_drawable_place_right;
            } else if (top && left && right) {
                resId = R.drawable.vector_drawable_place_top;
            } else if (bottom && left && right) {
                resId = R.drawable.vector_drawable_place_bottom;
            } else if (top && right) {
                resId = R.drawable.vector_drawable_place_top_right;
            } else if (top && left) {
                resId = R.drawable.vector_drawable_place_top_left;
            } else if (bottom && right) {
                resId = R.drawable.vector_drawable_place_bottom_right;
            } else if (bottom && left) {
                resId = R.drawable.vector_drawable_place_bottom_left;
            }
        }
        mMoveTaskButton.setImageResource(resId);
        mMoveTaskButton.setColorFilter(t.useLightOnPrimaryColor ?
                mTaskbarIconLightColor :
                mTaskbarIconDarkColor, Mode.SRC_ATOP);
    }

    /** Unbinds the bar view from the task */
    void unbindFromTask() {
        mApplicationIcon.setImageDrawable(null);
    }

    /** Animates this task bar dismiss button when launching a task. */
    void startLaunchTaskDismissAnimation() {
        if (mDismissButton.getVisibility() == View.VISIBLE) {
            mDismissButton.animate().cancel();
            mDismissButton.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.taskViewExitToAppDuration)
                    .start();
        }
        if (Settings.System.getInt(mContext.getContentResolver(),
                       Settings.System.LOCK_TO_APP_ENABLED, 0) != 0) {
            mPinButton.setVisibility(View.VISIBLE);
            mPinButton.animate().cancel();
            mPinButton.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.taskViewExitToAppDuration)
                    .withLayer()
                    .start();
        }
        if (mFloatButton.getVisibility() == View.VISIBLE) {
            mFloatButton.animate().cancel();
            mFloatButton.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.taskViewExitToAppDuration)
                    .withLayer()
                    .start();
        }
    }

    /** Animates this task bar if the user does not interact with the stack after a certain time. */
    void startNoUserInteractionAnimation() {
        if (mDismissButton.getVisibility() != View.VISIBLE) {
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setAlpha(0f);
            mDismissButton.animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutLinearInInterpolator)
                    .setDuration(mConfig.taskViewEnterFromAppDuration)
                    .start();
        }
        if (Settings.System.getInt(mContext.getContentResolver(),
                       Settings.System.LOCK_TO_APP_ENABLED, 0) != 0) {
            mPinButton.setVisibility(View.VISIBLE);
            mPinButton.setAlpha(0f);
            mPinButton.animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutLinearInInterpolator)
                    .setDuration(mConfig.taskViewEnterFromAppDuration)
                    .withLayer()
                    .start();
        }
        /** If we disabled the floating button in settings, do not make it visible */
            if (mFloatButton.getVisibility() != View.VISIBLE) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.FLOATING_WINDOW_MODE, 0) == 1) {
                   mFloatButton.setVisibility(View.VISIBLE);
                } else {
                    mFloatButton.setVisibility(View.GONE);
                }
                mFloatButton.setAlpha(0f);
                mFloatButton.animate()
                        .alpha(1f)
                        .setStartDelay(0)
                        .setInterpolator(mConfig.fastOutLinearInInterpolator)
                        .setDuration(mConfig.taskViewEnterFromAppDuration)
                        .start();
        }
    }

    /** Mark this task view that the user does has not interacted with the stack after a certain time. */
    void setNoUserInteractionState() {
        if (mDismissButton.getVisibility() != View.VISIBLE) {
            mDismissButton.animate().cancel();
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setAlpha(1f);
        }
        if (Settings.System.getInt(mContext.getContentResolver(),
                       Settings.System.LOCK_TO_APP_ENABLED, 0) != 0) {
            mPinButton.setVisibility(View.VISIBLE);
            mPinButton.animate().cancel();
            mPinButton.setVisibility(View.VISIBLE);
            mPinButton.setAlpha(1f);
        }
        /** If we disabled the master float switch, do not make this visible */
        if (mFloatButton.getVisibility() != View.VISIBLE) {
            mFloatButton.animate().cancel();
            if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FLOATING_WINDOW_MODE, 0) == 1) {
                mFloatButton.setVisibility(View.VISIBLE);
            } else {
                mFloatButton.setVisibility(View.GONE);
            }
            mFloatButton.setAlpha(1f);
        }
    }

    /** Resets the state tracking that the user has not interacted with the stack after a certain time. */
    void resetNoUserInteractionState() {
        mDismissButton.setVisibility(View.INVISIBLE);
        mPinButton.setVisibility(View.GONE);
        mFloatButton.setVisibility(View.INVISIBLE);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {

        // Don't forward our state to the drawable - we do it manually in onTaskViewFocusChanged.
        // This is to prevent layer trashing when the view is pressed.
        return new int[] {};
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mLayersDisabled) {
            mLayersDisabled = false;
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    mLayersDisabled = false;
                    setLayerType(LAYER_TYPE_HARDWARE, mDimLayerPaint);
                }
            });
        }
    }

    public void disableLayersForOneFrame() {
        mLayersDisabled = true;

        // Disable layer for a frame so we can draw our first frame faster.
        setLayerType(LAYER_TYPE_NONE, null);
    }

    /** Notifies the associated TaskView has been focused. */
    void onTaskViewFocusChanged(boolean focused, boolean animateFocusedState) {
        // If we are not animating the visible state, just return
        if (!animateFocusedState) return;

        boolean isRunning = false;
        if (mFocusAnimator != null) {
            isRunning = mFocusAnimator.isRunning();
            Utilities.cancelAnimationWithoutCallbacks(mFocusAnimator);
        }

        if (focused) {
            int currentColor = mBackgroundColor;
            int secondaryColor = getSecondaryColor(mCurrentPrimaryColor, mCurrentPrimaryColorIsDark);
            int[][] states = new int[][] {
                    new int[] {},
                    new int[] { android.R.attr.state_enabled },
                    new int[] { android.R.attr.state_pressed }
            };
            int[] newStates = new int[]{
                    0,
                    android.R.attr.state_enabled,
                    android.R.attr.state_pressed
            };
            int[] colors = new int[] {
                    currentColor,
                    secondaryColor,
                    secondaryColor
            };
            mBackground.setColor(new ColorStateList(states, colors));
            mBackground.setState(newStates);
            // Pulse the background color
            int lightPrimaryColor = getSecondaryColor(mCurrentPrimaryColor, mCurrentPrimaryColorIsDark);
            ValueAnimator backgroundColor = ValueAnimator.ofObject(new ArgbEvaluator(),
                    currentColor, lightPrimaryColor);
            backgroundColor.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mBackground.setState(new int[]{});
                }
            });
            backgroundColor.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int color = (int) animation.getAnimatedValue();
                    mBackgroundColorDrawable.setColor(color);
                    mBackgroundColor = color;
                }
            });
            backgroundColor.setRepeatCount(ValueAnimator.INFINITE);
            backgroundColor.setRepeatMode(ValueAnimator.REVERSE);
            // Pulse the translation
            ObjectAnimator translation = ObjectAnimator.ofFloat(this, "translationZ", 15f);
            translation.setRepeatCount(ValueAnimator.INFINITE);
            translation.setRepeatMode(ValueAnimator.REVERSE);

            mFocusAnimator = new AnimatorSet();
            mFocusAnimator.playTogether(backgroundColor, translation);
            mFocusAnimator.setStartDelay(150);
            mFocusAnimator.setDuration(750);
            mFocusAnimator.start();
        } else {
            if (isRunning) {
                // Restore the background color
                int currentColor = mBackgroundColor;
                ValueAnimator backgroundColor = ValueAnimator.ofObject(new ArgbEvaluator(),
                        currentColor, mCurrentPrimaryColor);
                backgroundColor.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int color = (int) animation.getAnimatedValue();
                        mBackgroundColorDrawable.setColor(color);
                        mBackgroundColor = color;
                    }
                });
                // Restore the translation
                ObjectAnimator translation = ObjectAnimator.ofFloat(this, "translationZ", 0f);

                mFocusAnimator = new AnimatorSet();
                mFocusAnimator.playTogether(backgroundColor, translation);
                mFocusAnimator.setDuration(150);
                mFocusAnimator.start();
            } else {
                mBackground.setState(new int[] {});
                setTranslationZ(0f);
            }
        }
    }
}
