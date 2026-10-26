package justfatlard.pandorical.client.entitykind;

import justfatlard.pandorical.client.animation.AnimationLibrary;
import justfatlard.pandorical.client.animation.EntityAnimations;
import justfatlard.pandorical.client.entitymodel.EntityModelDefinition;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A model built from a model file, posed only by what the file says: its look part turned where the
 * entity looks, and its animations laid over the rest pose. At rest it plays its idle animation;
 * on the move its moving one, by the distance covered, taking over from the idle one as it speeds
 * up; its attack animation through a strike; and its hurt animation through the flinch from a hit,
 * over everything else.
 */
public class BodyModel extends EntityModel<BodyRenderState> {
    private final EntityModelDefinition.@Nullable Motion motion;
    private final @Nullable ModelPart look;

    public BodyModel(ModelPart root, EntityModelDefinition.@Nullable Motion motion) {
        super(root);
        this.motion = motion;
        String name = motion == null ? null : motion.look();
        this.look = name == null ? null : root.createPartLookup().apply(name);
    }

    @Override
    public void setupAnim(BodyRenderState state) {
        super.setupAnim(state);
        if (look != null) {
            look.yRot += state.yRot * Mth.DEG_TO_RAD;
            look.xRot += state.xRot * Mth.DEG_TO_RAD;
        }
        if (motion == null) return;

        float moving = 0F;
        if (motion.moving() != null) {
            moving = Math.min(1F, state.walkAnimationSpeed * motion.moving().strength());
            KeyframeAnimation swim = baked(motion.moving());
            if (swim != null) swim.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed,
                motion.moving().pace(), motion.moving().strength());
        }
        if (motion.idle() != null && moving < 1F) {
            KeyframeAnimation idle = baked(motion.idle());
            if (idle != null) idle.apply((long) (state.ageInTicks * 50F * motion.idle().pace()), 1F - moving);
        }
        if (motion.attack() != null && state.swing > 0F) through(motion.attack(), state.swing);
        if (motion.hurt() != null && state.hurt > 0F) through(motion.hurt(), state.hurt);
    }

    /** Plays a clip at {@code progress} of the way through it, nought to one. */
    private void through(EntityModelDefinition.Clip clip, float progress) {
        KeyframeAnimation animation = baked(clip);
        AnimationDefinition definition = AnimationLibrary.get(clip.animation());
        if (animation != null && definition != null) {
            animation.apply((long) (progress * definition.lengthInSeconds() * 1000F), 1F);
        }
    }

    /** Null until the animation has arrived with the server's assets. */
    private @Nullable KeyframeAnimation baked(EntityModelDefinition.Clip clip) {
        return EntityAnimations.baked(root(), clip.animation());
    }
}
