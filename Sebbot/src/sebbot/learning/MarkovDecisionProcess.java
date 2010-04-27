package sebbot.learning;

import sebbot.MathTools;
import sebbot.SoccerParams;
import sebbot.Vector2D;

/**
 * @author Sebastien Lentz
 *
 */
public class MarkovDecisionProcess
{
    public static final float BIG_REWARD = 1000000f;
    
    public static State nextState(State s, Action a)
    {
        return nextState(s, a, false);
    }
    
    public static State nextState(State s, Action a, boolean isDiscreteState)
    {
        State nextState;

        if (s.isTerminal())
        {
            nextState = s;
        }
        else
        {
            nextState = new State();

            /* ------------ First compute the useful vectors ---------------- */
            Vector2D ballVelocity = new Vector2D(s.getBallVelocityNorm(), s
                .getBallVelocityDirection(), true);

            Vector2D oldPlayerSpeed = new Vector2D(s.getPlayerVelocityNorm(), s
                .getPlayerVelocityDirection(), true);

            Vector2D newPlayerSpeed;
            if (a.isTurn())
            {
                newPlayerSpeed = oldPlayerSpeed;
            }
            else
            {
                Vector2D playerAcceleration = (new Vector2D(a.getValue()
                        * SoccerParams.DASH_POWER_RATE, s
                    .getPlayerBodyDirection(), true))
                    .normalize(SoccerParams.PLAYER_ACCEL_MAX);

                newPlayerSpeed = oldPlayerSpeed.add(playerAcceleration)
                    .normalize(SoccerParams.PLAYER_SPEED_MAX);
            }

            Vector2D oldRelPosition = new Vector2D(s.getRelativeDistance(),
                MathTools.normalizeAngle(s.getRelativeDirection()
                        + s.getPlayerBodyDirection()), true);

            Vector2D newRelPosition = oldRelPosition.add(ballVelocity)
                .subtract(newPlayerSpeed);

            /* ----- Now build the next state using the computed vectors ---- */
            nextState.setBallVelocityNorm(s.getBallVelocityNorm()
                    * SoccerParams.BALL_DECAY);

            nextState.setBallVelocityDirection(s.getBallVelocityDirection());

            nextState.setPlayerVelocityNorm((float) (newPlayerSpeed
                .polarRadius() * SoccerParams.PLAYER_DECAY));

            nextState.setPlayerVelocityDirection((float) newPlayerSpeed
                .polarAngle());

            nextState.setPlayerBodyDirection((float) MathTools.normalizeAngle(s
                .getPlayerBodyDirection()
                    + (a.isTurn() ? a.getValue() : 0.0f)));

            nextState.setRelativeDistance((float) newRelPosition.polarRadius());

            nextState.setRelativeDirection((float) MathTools
                .normalizeAngle(newRelPosition.polarAngle()
                        - nextState.getPlayerBodyDirection()));
            
            if (isDiscreteState)
            {
                nextState.discretize();
            }
        }

        return nextState; //TODO change for non discrete!
    }

    public static float reward(State s, Action a)
    {
        return reward(s, a, false);
    }
    
    public static float reward(State s, Action a, boolean isDiscreteState)
    {
        float reward;

        if (s.isTerminal())
        {
            reward = 0.0f;
        }
        else
        {
            State nextState = nextState(s, a, isDiscreteState);
            
            float nextStepDistance = nextState.getRelativeDistance();

            if (nextStepDistance < SoccerParams.KICKABLE_MARGIN)
            {
                reward = BIG_REWARD;
            }
            else
            {
                reward =  0f-nextStepDistance; //0.0f - nextStepDistance;
            }
        }

        return reward;
    }

    public static float trajectoryReward(State initialState, Policy p, int nbOfSteps)
    {
        float discountFactor = 0.95f;
//        LinkedList<State> ls = new LinkedList<State>();
//        LinkedList<Action> la = new LinkedList<Action>();

        State s = initialState;
        Action a = p.chooseAction(s);        
        float reward = reward(s, a);
        int nbOfIterations = 1;
        while (!s.isTerminal() && nbOfIterations < nbOfSteps)
        {
            s = nextState(s, a);
            a = p.chooseAction(s);

//            ls.addLast(s);
//            la.addLast(a);

            reward += discountFactor * reward(s, a);
            discountFactor *= discountFactor;
            s = nextState(s, a);
            nbOfIterations++;
        }

//        if (reward < 0 && reward != -5000f)
//        {
//            while (!ls.isEmpty())
//            {
//                System.out.println(ls.removeFirst() + " : " + la.removeFirst());
//            }
//        }

//        System.out.println("nb of it: " + nbOfIterations + " | score: " + reward);
        return reward;
    }

}
