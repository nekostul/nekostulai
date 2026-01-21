package ru.nekostul.nekostulai.nekostulnpc.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulnpc.ModNPCEntities;
import ru.nekostul.nekostulai.nekostulnpc.nekostulNPC;

@Mod.EventBusSubscriber(
        modid = "nekostulai",
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class NekostulNPCRenderer
        extends MobRenderer<nekostulNPC, PlayerModel<nekostulNPC>> {

    private static final ResourceLocation STEVE =
            new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

    public NekostulNPCRenderer(EntityRendererProvider.Context ctx) {
        super(
                ctx,
                new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                0.5f
        );
    }

    @Override
    public ResourceLocation getTextureLocation(nekostulNPC entity) {
        return STEVE;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModNPCEntities.NEKOSTUL_NPC.get(),
                NekostulNPCRenderer::new
        );
    }
}