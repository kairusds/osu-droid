package ru.nsu.ccfit.zuev.osu.game;

import android.graphics.PointF;

import com.osudroid.utils.Execution;
import com.reco1l.andengine.sprite.UISprite;
import com.reco1l.andengine.modifier.Modifiers;
import com.reco1l.andengine.Anchor;
import com.rian.osu.beatmap.hitobject.Spinner;

import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.util.MathUtils;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.Constants;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.scoring.ScoreNumber;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;

/**
 * Created by dgsrz on 15/10/19.
 */
public class GameplayModernSpinner extends GameplaySpinner {

    private final UISprite middle;
    private final UISprite middle2;
    private final UISprite bottom;
    private final UISprite top;
    private final UISprite glow;
    private final ScoreNumber bonusScore;

    public GameplayModernSpinner() {
        ResourceManager.getInstance().checkEvoSpinnerTextures();
        position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f);
        Utils.trackToRealCoords(position);

        middle = new UISprite();
        middle.setOrigin(Anchor.Center);
        middle.setPosition(position.x, position.y);
        middle.setTextureRegion(ResourceManager.getInstance().getTexture("spinner-middle"));

        middle2 = new UISprite();
        middle2.setOrigin(Anchor.Center);
        middle2.setPosition(position.x, position.y);
        middle2.setTextureRegion(ResourceManager.getInstance().getTexture("spinner-middle2"));

        bottom = new UISprite();
        bottom.setOrigin(Anchor.Center);
        bottom.setPosition(position.x, position.y);
        bottom.setTextureRegion(ResourceManager.getInstance().getTexture("spinner-bottom"));

        top = new UISprite();
        top.setOrigin(Anchor.Center);
        top.setPosition(position.x, position.y);
        top.setTextureRegion(ResourceManager.getInstance().getTexture("spinner-top"));

        glow = new UISprite();
        glow.setOrigin(Anchor.Center);
        glow.setPosition(position.x, position.y);
        glow.setTextureRegion(ResourceManager.getInstance().getTexture("spinner-glow"));

        bonusScore = new ScoreNumber(position.x, position.y + 100, "", 1.1f, true);

        // Spinners always end combo.
        endsCombo = true;
    }

    @Override
    public void init(final GameObjectListener listener, final Scene scene,
                     final Spinner beatmapSpinner, final float rps, final StatisticV2 stat) {
        this.scene = scene;
        this.beatmapSpinner = beatmapSpinner;
        this.listener = listener;
        this.stat = stat;
        duration = Math.max((float) beatmapSpinner.getDuration() / 1000f, 0);
        needRotations = rps * duration;

        if (duration < 0.05f) {
            needRotations = 0.1f;
        }

        clear = duration <= 0f;
        fullRotations = 0;
        rotations = 0;
        bonusScoreCounter = 1;
        spinnable = false;

        reloadHitSounds();

        glow.setAlpha(0f);
        glow.setScale(0.9f);
        glow.setColor(0f, 0.8f, 1f);

        boolean isBackgroundVisible = !GameHelper.isTraceable() ||
            (Config.isShowFirstApproachCircle() && beatmapSpinner.isFirstNote());

        middle.setVisible(isBackgroundVisible);
        if (middle.isVisible()) {
            middle.setAlpha(0f);
            middle.setScale(0.9f);
        }

        middle2.setVisible(isBackgroundVisible);
        if (middle2.isVisible()) {
            middle2.setAlpha(0f);
            middle2.setScale(0.9f);
        }

        bottom.setAlpha(0f);
        bottom.setScale(0.9f);

        top.setAlpha(0f);
        top.setScale(0.9f);

        scene.attachChild(glow);
        scene.attachChild(bottom);
        scene.attachChild(top);
        scene.attachChild(middle);
        scene.attachChild(middle2);

        float timePreempt = (float) beatmapSpinner.timePreempt / 1000f;

        top.registerEntityModifier(Modifiers.sequence(e -> Execution.updateThread(this::removeFromScene),
            Modifiers.fadeIn(timePreempt, e -> {
                    spinnable = true;
                    listener.onSpinnerStart(id);
            }),
            Modifiers.delay(duration)
        ));

        bottom.registerEntityModifier(Modifiers.fadeIn(timePreempt));
        middle.registerEntityModifier(Modifiers.fadeIn(timePreempt));
        middle2.registerEntityModifier(Modifiers.fadeIn(timePreempt));
    }

    @Override
    public void update(float dt) {
        // Allow the spinner to fully fade in first before receiving spins.
        if (!spinnable) {
            return;
        }

        updateSamples(dt);
        PointF mouse = null;

        for (int i = 0, count = listener.getCursorsCount(); i < count; ++i) {
            if (mouse == null) {
                if (autoPlay) {
                    mouse = position;
                } else if (listener.isMouseDown(i)) {
                    mouse = listener.getMousePos(i);
                } else {
                    continue;
                }
                currMouse.set(mouse.x - position.x, mouse.y - position.y);
            }

            if (oldMouse == null || listener.isMousePressed(this, i)) {
                if (oldMouse == null) {
                    oldMouse = new PointF();
                }
                oldMouse.set(currMouse);
                return;
            }
        }

        if (mouse == null)
            return;

        float degree = MathUtils.radToDeg(Utils.direction(currMouse));
        top.setRotation(degree);
        bottom.setRotation(degree / 2);

        var len1 = Utils.length(currMouse);
        var len2 = Utils.length(oldMouse);
        var dFill = (currMouse.x / len1) * (oldMouse.y / len2) - (currMouse.y / len1) * (oldMouse.x / len2);

        if (Math.abs(len1) < 0.0001f || Math.abs(len2) < 0.0001f)
            dFill = 0;

        if (autoPlay) {
            dFill = 5 * 4 * dt;
            degree = (rotations + dFill / 4f) * 360;
            top.setRotation(degree);
            //auto时，FL光圈绕中心旋转
            if (GameHelper.isAutopilot() || GameHelper.isAutoplay()) {
                float pX = position.x + 50 * (float) Math.sin(degree);
                float pY = position.y + 50 * (float) Math.cos(degree);
                listener.updateAutoBasedPos(pX, pY);
            }
            // bottom.setRotation(-degree);
        }

        rotations += dFill / 4f;
        float percentFilled = (Math.abs(rotations) + fullRotations) / needRotations;
        float percent = Math.min(percentFilled, 1);

        if (dFill != 0) {
            updateSpinSampleFrequency(percentFilled);
            spinnerSpinSample.play();
        } else {
            spinnerSpinSample.stop();
        }

        middle.setColor(1, 1 - percent, 1 - percent);
        top.setScale(0.9f + percent * 0.1f);
        bottom.setScale(0.9f + percent * 0.1f);

        if (middle.isVisible()) {
            middle.setScale(0.9f + percent * 0.1f);
        }

        if (middle2.isVisible()) {
            middle2.setScale(0.9f + percent * 0.1f);
        }

        glow.setAlpha(percent * 0.8f);
        glow.setScale(0.9f + percent * 0.1f);

        if (percentFilled > 1 || clear) {
            if (!clear) {
                // Clear Sprite
                clear = true;
            } else if (Math.abs(rotations) > 1) {
                rotations -= 1 * Math.signum(rotations);
                bonusScore.setText(String.valueOf(bonusScoreCounter * 1000));
                listener.onSpinnerHit(id, 1000, false, 0);
                bonusScoreCounter++;
                if (!bonusScore.hasParent()) {
                    scene.attachChild(bonusScore);
                }
                spinnerBonusSample.play();
                glow.registerEntityModifier(
                    Modifiers.sequence(
                        Modifiers.color(0.1f, 0f, 1f, 0.8f, 1f, 1f, 1f),
                        Modifiers.color(0.1f, 1f, 0f, 1f, 0.8f, 1f, 1f)
                    )
                );
                float rate = 0.375f;
                if (GameHelper.getHealthDrain() > 0) {
                    rate = 1 + (GameHelper.getHealthDrain() / 4f);
                }
                stat.changeHp(rate * 0.01f * duration / needRotations);
            }
        } else if (Math.abs(rotations) > 1) {
            rotations -= 1 * Math.signum(rotations);
            if (replayObjectData == null || replayObjectData.accuracy / 4 > fullRotations) {
                fullRotations++;
                stat.registerSpinnerHit();
                float rate = 0.375f;
                if (GameHelper.getHealthDrain() > 0) {
                    rate = 1 + (GameHelper.getHealthDrain() / 2f);
                }
                stat.changeHp(rate * 0.01f * duration / needRotations);
            }
        }

        oldMouse.set(currMouse);
    }

    public void removeFromScene() {
        middle.clearEntityModifiers();
        scene.detachChild(middle);

        middle2.clearEntityModifiers();
        scene.detachChild(middle2);

        bottom.clearEntityModifiers();
        scene.detachChild(bottom);

        top.clearEntityModifiers();
        scene.detachChild(top);

        glow.clearEntityModifiers();
        scene.detachChild(glow);

        scene.detachChild(bonusScore);

        listener.removeObject(GameplayModernSpinner.this);
        GameObjectPool.getInstance().putSpinner(this);

        int score = 0;
        if (replayObjectData != null) {
            if (fullRotations < replayObjectData.accuracy / 4)
                fullRotations = replayObjectData.accuracy / 4;
            if (fullRotations >= needRotations)
                clear = true;
            int bonusRot = (int) (replayObjectData.accuracy / 4f - needRotations + 1);
            while (bonusRot < score) {
                bonusRot++;
                listener.onSpinnerHit(id, 1000, false, 0);
            }
        }
        float percentFilled = (Math.abs(rotations) + fullRotations)
                / needRotations;
        if (percentFilled > 0.9f) {
            score = 50;
        }
        if (percentFilled > 0.95f) {
            score = 100;
        }
        if (clear) {
            score = 300;
        }
        if (replayObjectData != null) {
            score = switch (replayObjectData.accuracy % 4) {
                case 0 -> 0;
                case 1 -> 50;
                case 2 -> 100;
                case 3 -> 300;
                default -> score;
            };
        }
        stopLoopingSamples();
        listener.onSpinnerHit(id, score, endsCombo, this.bonusScoreCounter + fullRotations - 1);
        playAndFreeHitSamples(score);
    }
}
