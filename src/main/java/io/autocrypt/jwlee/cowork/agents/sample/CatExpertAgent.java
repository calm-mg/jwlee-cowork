package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import io.autocrypt.jwlee.cowork.service.CatFactService;

@Agent(description = "A cat expert who provides personalized cat facts based on user's lover profile")
public class CatExpertAgent {

    private final CatFactService catFactService;

    public CatExpertAgent(CatFactService catFactService) {
        this.catFactService = catFactService;
    }

    // --- Domain Object ---
    
    /**
     * Represents the user's cat-loving personality.
     * DICE: Methods annotated with @LlmTool are callable by the LLM.
     */
    public record CatLover(String personality, String interest, int enthusiasmLevel) {
        
        @LlmTool(description = "Generate a personalized greeting for this cat lover")
        public String getGreeting() {
            return String.format("Meow! Greetings to our %s friend who loves %s!", personality, interest);
        }

        public String toSummary() {
            return String.format("A %s who is interested in %s (Enthusiasm: %d/10)", 
                    personality, interest, enthusiasmLevel);
        }
    }

    public record CatFactResponse(String message) {}

    // --- Actions ---

    /**
     * Step 1: Analyze user input to create a CatLover profile.
     */
    @Action
    public CatLover characterizeLover(UserInput input, Ai ai) {
        return ai.withLlmByRole("normal")
                .createObject(String.format("""
                        Analyze the user's description and create a CatLover profile.
                        
                        # USER DESCRIPTION: %s
                        
                        Fields:
                        - personality: (e.g., "Scientific Researcher", "Cozy Homebody", "Wild Adventurer")
                        - interest: (e.g., "History", "Health", "Fun Trivia")
                        - enthusiasmLevel: (1-10)
                        """, input.getContent()), CatLover.class);
    }

    /**
     * Step 2: Use the CatLover profile and CatFactService to generate a fun response.
     */
    @AchievesGoal(description = "The user is entertained with cat facts tailored to their profile")
    @Action
    public CatFactResponse entertainLover(CatLover lover, Ai ai) {
        String prompt = String.format("""
                You are a cat expert speaking to a fellow cat lover.
                
                <lover_profile>
                %s
                </lover_profile>
                
                <instruction>
                1. Start by calling 'getGreeting' to get a personalized opening.
                2. Use 'getCatFacts' tool to fetch 2-3 facts.
                3. Based on the <lover_profile>, explain these facts in a way they would appreciate.
                4. Use the personality and interest from the profile to tone your response.
                5. ALL RESPONSES MUST BE IN KOREAN.
                </instruction>
                """, lover.toSummary());

        return ai.withLlmByRole("cheapest")
                .withToolObject(catFactService) // DICE: Hands (API tool)
                .withToolObject(lover)         // DICE: Hands (Domain tool)
                .createObject(prompt, CatFactResponse.class);
    }
}
