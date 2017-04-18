package cn.carbs.android.expandabletextview.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/**
 * 2端对齐、超过指定行数显示“...全文”
 */
public class JustifyTextView extends TextView {

    private int mLineY;
    private int mViewWidth;

    public JustifyTextView(Context context) {
        super(context);
        init();
    }

    public JustifyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JustifyTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver obs = getViewTreeObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    obs.removeOnGlobalLayoutListener(this);
                } else {
                    obs.removeGlobalOnLayoutListener(this);
                }
                setTextInternal(getNewTextByConfig(), mBufferType);
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TextPaint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        // 返回绘制状态的资源ID数组表示视图的当前状态
        paint.drawableState = getDrawableState();
        // 对View上的内容进行测量后得到的View内容占据的宽度
        // 前提是你必须在父布局的onLayout()方法或者此View的onDraw()方法里调用measure(0,0);
        // 否则你得到的结果和getWidth()得到的结果一样。
        mViewWidth = getMeasuredWidth();
        // 获取文本
        String text = getText().toString();
        mLineY = 0;
        mLineY += getTextSize();
        // 获取用于显示当前文本的布局
        Layout layout = getLayout();

        if (layout == null) {
            return;
        }

        Paint.FontMetrics fm = paint.getFontMetrics();

        int textHeight = (int) (Math.ceil(fm.descent - fm.ascent));
        textHeight = (int) (textHeight * layout.getSpacingMultiplier() + layout.getSpacingAdd()) - 2;

        for (int i = 0; i < layout.getLineCount(); i++) {
            // 返回文本中的指定行开头的偏移
            int lineStart = layout.getLineStart(i);
            // 返回文本中的指定行最后一个字符的偏移
            int lineEnd = layout.getLineEnd(i);
            float width = StaticLayout.getDesiredWidth(text, lineStart, lineEnd, getPaint());
            String line = text.substring(lineStart, lineEnd);

            if (line.equals("")) {
                break;
            }

            if (i < layout.getLineCount() - 1) {
                if (needScale(line)) {
                    drawScaledText(canvas, lineStart, line, width);
                } else {
                    canvas.drawText(line, 0, mLineY, paint);
                }
            } else {
                canvas.drawText(line, 0, mLineY, paint);
                if (line.endsWith(mToExpandHint)) {
                    if (expandPaint == null) {
                        expandPaint = new TextPaint(paint);
                        expandPaint.setColor(Color.BLUE);
                    }
                    float xAxisLeft = layout.getPrimaryHorizontal(getText().length() - mToExpandHint.length());
                    canvas.drawText(mToExpandHint, xAxisLeft, mLineY, expandPaint);
                }
            }

            // 增加行高
            mLineY += textHeight;
        }
    }

    //末尾“全文”字样的颜色
    private TextPaint expandPaint;

    private void drawScaledText(Canvas canvas, int lineStart, String line,
                                float lineWidth) {
        float x = 0;
        if (isFirstLineOfParagraph(lineStart, line)) {
            String blanks = "  ";
            canvas.drawText(blanks, x, mLineY, getPaint());
            float bw = StaticLayout.getDesiredWidth(blanks, getPaint());
            x += bw;

            line = line.substring(3);
        }

        int gapCount = line.length() - 1;
        int i = 0;
        if (line.length() > 2 && line.charAt(0) == 12288
                && line.charAt(1) == 12288) {
            String substring = line.substring(0, 2);
            float cw = StaticLayout.getDesiredWidth(substring, getPaint());
            canvas.drawText(substring, x, mLineY, getPaint());
            x += cw;
            i += 2;
        }

        float d = (mViewWidth - lineWidth) / gapCount;
        for (; i < line.length(); i++) {
            String c = String.valueOf(line.charAt(i));
            float cw = StaticLayout.getDesiredWidth(c, getPaint());
            canvas.drawText(c, x, mLineY, getPaint());
            x += cw + d;
        }
    }

    @Override
    public void setText(CharSequence text, TextView.BufferType type) {
        mOrigText = text;
        mBufferType = type;
        setTextInternal(getNewTextByConfig(), type);
    }

    private void setTextInternal(CharSequence text, TextView.BufferType type) {
        super.setText(text, type);
    }

    private TextPaint mTextPaint;
    private TextView.BufferType mBufferType = TextView.BufferType.NORMAL;
    //  the original text of this view
    private CharSequence mOrigText;

    private Layout mLayout;
    private int mTextLineCount = -1;
    private int mLayoutWidth = 0;
    private int mMaxLinesOnShrink = 4;
    private int mFutureTextViewWidth = 0;
    private String mEllipsisHint = "...";
    private String mToExpandHint = "全文";
    private String mGapToExpandHint = "";
    private boolean mShowToExpandHint = true;

    ForegroundColorSpan mForeSpan = new ForegroundColorSpan(Color.BLUE);

    /**
     * refresh and get a will-be-displayed text by current configuration
     *
     * @return get a will-be-displayed text
     */
    private CharSequence getNewTextByConfig() {
        if (TextUtils.isEmpty(mOrigText)) {
            return mOrigText;
        }

        mLayout = getLayout();
        if (mLayout != null) {
            mLayoutWidth = mLayout.getWidth();
        }

        if (mLayoutWidth <= 0) {
            if (getWidth() == 0) {
                if (mFutureTextViewWidth == 0) {
                    return mOrigText;
                } else {
                    mLayoutWidth = mFutureTextViewWidth - getPaddingLeft() - getPaddingRight();
                }
            } else {
                mLayoutWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            }
        }

        mTextPaint = getPaint();

        mTextLineCount = -1;
        mLayout = new DynamicLayout(mOrigText, mTextPaint, mLayoutWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        mTextLineCount = mLayout.getLineCount();

        if (mTextLineCount <= mMaxLinesOnShrink) {
            return mOrigText;
        }
        int indexEnd = getValidLayout().getLineEnd(mMaxLinesOnShrink - 1);
        int indexStart = getValidLayout().getLineStart(mMaxLinesOnShrink - 1);

        int remainWidth = getValidLayout().getWidth() -
                (int) (mTextPaint.measureText(mOrigText.subSequence(indexStart, indexEnd).toString()) + 0.5);
        float widthTailReplaced = mTextPaint.measureText(getContentOfString(mEllipsisHint)
                + (mShowToExpandHint ? (getContentOfString(mToExpandHint) + getContentOfString(mGapToExpandHint)) : ""));

        int indexEndTrimmedRevised = indexEnd;
        if (remainWidth < widthTailReplaced) {

            int extraOffset = 0;
            int extraWidth = 0;
            while (remainWidth + extraWidth < widthTailReplaced) {
                extraOffset--;
                if (indexEndTrimmedRevised + extraOffset > indexStart) {
                    extraWidth = (int) (mTextPaint.measureText(mOrigText.subSequence(indexEndTrimmedRevised + extraOffset, indexEndTrimmedRevised).toString()) + 0.5);
                } else {
                    break;
                }
            }
            indexEndTrimmedRevised += extraOffset;
        }

        String fixText = removeEndLineBreak(mOrigText.subSequence(0, indexEndTrimmedRevised));
        SpannableStringBuilder ssbShrink = new SpannableStringBuilder(fixText)
                .append(mEllipsisHint);
        if (mShowToExpandHint) {
            ssbShrink.append(getContentOfString(mGapToExpandHint) + getContentOfString(mToExpandHint));
        }
        return ssbShrink;
    }

    private Layout getValidLayout() {
        return mLayout != null ? mLayout : getLayout();
    }

    private int getLengthOfString(String string) {
        if (string == null)
            return 0;
        return string.length();
    }

    private String getContentOfString(String string) {
        if (string == null)
            return "";
        return string;
    }

    private String removeEndLineBreak(CharSequence text) {
        String str = text.toString();
        while (str.endsWith("\n")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    private boolean isFirstLineOfParagraph(int lineStart, String line) {
        return line.length() > 3 && line.charAt(0) == ' ' && line.charAt(1) == ' ';
    }

    private boolean needScale(String line) {
        if (line.length() == 0) {
            return false;
        } else {
            return line.charAt(line.length() - 1) != '\n';
        }
    }

}