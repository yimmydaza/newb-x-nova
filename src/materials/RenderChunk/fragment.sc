$input v_color0, v_color1, v_fog, v_refl, v_texcoord0, v_lightmapUV, v_extra

#include <bgfx_shader.sh>
#include <newb/main.sh>

// AÑADIDO: Nuevas variables uniform para detectar el entorno.
uniform vec4 FogAndDistanceControl;
uniform vec4 FogColor;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

void main() {
   // AÑADIDO: Lógica de detección de entorno y variables necesarias para las sombras.
   vec2 uvl = v_lightmapUV;
   nl_environment env = nlDetectEnvironment(FogColor.rgb, FogAndDistanceControl.xyz);
   nl_skycolor skycol = nlSkyColors(env, FogColor.rgb);

   #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING)
   gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
   return;
   #endif

   vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
   vec4 color = v_color0;

   #ifdef ALPHA_TEST
   if (diffuse.a < 0.6) {
      discard;
   }
   #endif

   // AÑADIDO: El nuevo y avanzado sistema de sombras del archivo 2.
   float shadow = smoothstep(0.875, 0.860, pow(v_lightmapUV.y, 2.0));
   shadow = mix(shadow, 0.2, env.rainFactor);
   shadow = mix(shadow, 0.0, pow(uvl.x * 1.2, 6.0));
   diffuse.rgb *= 1.0-0.2*shadow;

   #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
   diffuse.rgb *= mix(vec3(1.0, 1.0, 1.0), texture2D(s_SeasonsTexture, v_color1.xy).rgb * 2.0, v_color1.z);
   #endif



   vec3 glow = nlGlow(s_MatTexture, v_texcoord0, v_extra.a);

   diffuse.rgb *= diffuse.rgb;

   vec3 lightTint = texture2D(s_LightMapTexture, v_lightmapUV).rgb;
   lightTint = mix(lightTint.bbb, lightTint*lightTint, 0.35 + 0.65*v_lightmapUV.y*v_lightmapUV.y*v_lightmapUV.y);

   color.rgb *= lightTint;

   #if defined(TRANSPARENT) && !(defined(SEASONS) || defined(RENDER_AS_BILLBOARDS))
   if (v_extra.b > 0.9) {
      diffuse.rgb = vec3_splat(1.0 - NL_WATER_TEX_OPACITY*(1.0 - diffuse.b*1.8));
      diffuse.a = color.a;
   }
   #else
   diffuse.a = 1.0;
   #endif

   diffuse.rgb *= color.rgb;
   diffuse.rgb += glow;

   if (v_extra.b > 0.9) {
      diffuse.rgb += v_refl.rgb*v_refl.a;
   } else if (v_refl.a > 0.0) {
      // reflective effect - only on xz plane
      float dy = abs(dFdy(v_extra.g));
      if (dy < 0.0002) {
         float mask = v_refl.a*(clamp(v_extra.r*10.0, 8.2, 8.8)-7.8);
         // OPCIONAL PERO RECOMENDADO: Usar 0.5 como en el archivo 2 para un mejor balance.
         diffuse.rgb *= 1.0 - 0.5*mask; 
         diffuse.rgb += v_refl.rgb*mask;
      }
   }

   // ELIMINADO: El antiguo sistema de oscurecimiento ha sido removido
   // porque el nuevo sistema de sombras lo reemplaza y mejora.

   diffuse.rgb = mix(diffuse.rgb, v_fog.rgb, v_fog.a);

   diffuse.rgb = colorCorrection(diffuse.rgb);

   gl_FragColor = diffuse;
}
