<v-app>
    <v-layout row>
        <v-flex>
            <v-container style="background: #ebebeb" class="h-full">
                <v-layout column>
                    <v-form>
                        <BrowserTest :test="svgTest"></BrowserTest>
                        <BrowserTest :test="speedTest"></BrowserTest>
                        <BrowserTest :test="captchaTest"></BrowserTest>
                        <AMTLoginButton></AMTLoginButton>
                    </v-form>
                </v-layout>
            </v-container>
        </v-flex>
    </v-layout>
</v-app>

