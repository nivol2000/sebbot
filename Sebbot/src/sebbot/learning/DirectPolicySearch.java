package sebbot.learning;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import sebbot.MathTools;
import sebbot.SoccerParams;

/**
 * This class implements a direct policy search algorithm for the ball capture
 * problem. Basic functions are optimized using cross-entropy.
 * To understand how the algorithm works, please refer to the article from
 * <a href=http://www.montefiore.ulg.ac.be/~ernst/>Damien Ernst</a>.
 * 
 * @see <a href=http://www.montefiore.ulg.ac.be/~ernst/adprl-ceps.pdf>
 * "Policy search with cross-entropy optimization of basis functions"
 * L. Busoniu, D. Ernst, R. Babuska and B. De Schutter</a>
 * 
 * @author Sebastien Lentz
 *
 */
public class DirectPolicySearch implements Policy, Serializable, Runnable
{
    private static final long             serialVersionUID = 8074266714836534435L;

    Random                                random;

    int                                   totalNbOfIterations;
    long                                  totalComputationTime;

    float[]                               averageScores;
    int[]                                 nbOfBadStates;

    int                                   nbOfDiscreteActions;
    int                                   nbOfBasicFunctions;
    int                                   nbOfSamples;
    float                                 percentageOfGoodSamples;

    float[][]                             centersMeans;
    float[][]                             centersStdDevs;
    float[][]                             radiiMeans;
    float[][]                             radiiStdDevs;
    float[][]                             bernoulliMeans;

    RadialGaussian[]                      basicFunctions;
    ArrayList<LinkedList<RadialGaussian>> actionToBasicFunctions;
    LinkedList<State>                     initialStates;
    LinkedList<State>                     performanceTestStates;

    /*
     * =========================================================================
     * 
     *                     Constructors and destructors
     * 
     * =========================================================================
     */
    public DirectPolicySearch(int nbOfBasicFunctions, int nbOfSamples)
    {
        this.totalNbOfIterations = 0;
        this.totalComputationTime = 0;
        this.averageScores = new float[2];
        this.nbOfBadStates = new int[2];
        this.averageScores[0] = 0f;
        this.averageScores[1] = 0f;
        this.nbOfBadStates[0] = 0;
        this.nbOfBadStates[1] = 0;

        this.percentageOfGoodSamples = 0.05f;
        this.random = new Random();
        this.nbOfDiscreteActions = (Action.getTurnSteps() + Action
            .getDashSteps());
        this.nbOfBasicFunctions = nbOfBasicFunctions;

        int nbOfBits = (int) (Math.ceil(Math.log(nbOfDiscreteActions)
                / Math.log(2.0d)));

        this.nbOfSamples = nbOfSamples;

        this.basicFunctions = new RadialGaussian[nbOfBasicFunctions];
        this.centersMeans = new float[nbOfBasicFunctions][7];
        this.centersStdDevs = new float[nbOfBasicFunctions][7];
        this.radiiMeans = new float[nbOfBasicFunctions][7];
        this.radiiStdDevs = new float[nbOfBasicFunctions][7];
        this.bernoulliMeans = new float[nbOfBasicFunctions][nbOfBits];
        this.actionToBasicFunctions = new ArrayList<LinkedList<RadialGaussian>>();

        for (int i = 0; i < nbOfDiscreteActions; i++)
        {
            actionToBasicFunctions.add(new LinkedList<RadialGaussian>());
        }

        for (int i = 0; i < nbOfBasicFunctions; i++)
        {
            basicFunctions[i] = new RadialGaussian(nbOfDiscreteActions - 1
                    - (i % nbOfDiscreteActions));

            centersMeans[i] = basicFunctions[i].getCenters();
            radiiMeans[i] = basicFunctions[i].getRadii();

            centersStdDevs[i][0] = SoccerParams.BALL_SPEED_MAX / 2.0f;
            centersStdDevs[i][1] = 180f / 2.0f;
            centersStdDevs[i][2] = SoccerParams.PLAYER_SPEED_MAX / 2.0f;
            centersStdDevs[i][3] = 180f / 2.0f;
            centersStdDevs[i][4] = 180f / 2.0f;
            centersStdDevs[i][5] = 125f / 2.0f;
            centersStdDevs[i][6] = 180f / 2.0f;
            radiiStdDevs[i][0] = SoccerParams.BALL_SPEED_MAX / 2.0f;
            radiiStdDevs[i][1] = 180f / 2.0f;
            radiiStdDevs[i][2] = SoccerParams.PLAYER_SPEED_MAX / 2.0f;
            radiiStdDevs[i][3] = 180f / 2.0f;
            radiiStdDevs[i][4] = 180f / 2.0f;
            radiiStdDevs[i][5] = 180f / 2.0f;
            radiiStdDevs[i][6] = 180f / 2.0f;

            for (int j = 0; j < nbOfBits; j++)
            {
                bernoulliMeans[i][j] = 0.5f;
            }

            actionToBasicFunctions.get(basicFunctions[i].getDiscreteActionNb())
                .add(basicFunctions[i]);
        }

        initialStates = new LinkedList<State>();
        performanceTestStates = new LinkedList<State>();
        generateStates();

        //computeOptimalParameters();
        //loadBFs("savedBFs.zip");
    }

    /*
     * =========================================================================
     * 
     *                      Getters and Setters
     * 
     * =========================================================================
     */
    /**
     * @return the nbOfSamples
     */
    public int getNbOfSamples()
    {
        return nbOfSamples;
    }

    /**
     * @param nbOfSamples the nbOfSamples to set
     */
    public void setNbOfSamples(int nbOfSamples)
    {
        this.nbOfSamples = nbOfSamples;
    }

    /**
     * @return the serialversionuid
     */
    public static long getSerialversionuid()
    {
        return serialVersionUID;
    }

    /**
     * @return the random
     */
    public Random getRandom()
    {
        return random;
    }

    /**
     * @return the totalNbOfIterations
     */
    public int getTotalNbOfIterations()
    {
        return totalNbOfIterations;
    }

    /**
     * @return the totalComputationTime
     */
    public long getTotalComputationTime()
    {
        return totalComputationTime;
    }

    /**
     * @return the nbOfDiscreteActions
     */
    public int getNbOfDiscreteActions()
    {
        return nbOfDiscreteActions;
    }

    /**
     * @return the nbOfBasicFunctions
     */
    public int getNbOfBasicFunctions()
    {
        return nbOfBasicFunctions;
    }

    /**
     * @return the percentageOfGoodSamples
     */
    public float getPercentageOfGoodSamples()
    {
        return percentageOfGoodSamples;
    }

    /**
     * @return the centersMeans
     */
    public float[][] getCentersMeans()
    {
        return centersMeans;
    }

    /**
     * @return the centersStdDevs
     */
    public float[][] getCentersStdDevs()
    {
        return centersStdDevs;
    }

    /**
     * @return the radiiMeans
     */
    public float[][] getRadiiMeans()
    {
        return radiiMeans;
    }

    /**
     * @return the radiiStdDevs
     */
    public float[][] getRadiiStdDevs()
    {
        return radiiStdDevs;
    }

    /**
     * @return the bernoulliMeans
     */
    public float[][] getBernoulliMeans()
    {
        return bernoulliMeans;
    }

    /**
     * @return the basicFunctions
     */
    public RadialGaussian[] getBasicFunctions()
    {
        return basicFunctions;
    }

    /**
     * @return the actionToBasicFunctions
     */
    public ArrayList<LinkedList<RadialGaussian>> getActionToBasicFunctions()
    {
        return actionToBasicFunctions;
    }

    /**
     * @return the initialStates
     */
    public LinkedList<State> getInitialStates()
    {
        return initialStates;
    }

    /*
     * =========================================================================
     * 
     *                            Main methods
     * 
     * =========================================================================
     */
    /**
     *
     */
    public void run()
    {
        computeOptimalParameters();
    }

    /**
     *
     */
    public Action chooseAction(State s)
    {
        int actionNb = 0;
        float bestScore = -1000000.0f;
        float score;
        Iterator<RadialGaussian> it;

        for (int i = 0; i < actionToBasicFunctions.size(); i++)
        {
            score = 0.0f;
            it = actionToBasicFunctions.get(i).iterator();
            while (it.hasNext())
            {
                score += it.next().f(s);
            }

            if (score > bestScore)
            {
                bestScore = score;
                actionNb = i;
            }
        }

        return new Action(actionNb);
    }

    /**
     * 
     */
    public void computeOptimalParameters()
    {
        long startTime;
        long finishTime;

        int nbOfBits = (int) (Math.ceil(Math.log(nbOfDiscreteActions)
                / Math.log(2.0d)));
        float[][][][] epsilonSamples = new float[nbOfSamples][nbOfBasicFunctions][2][7];
        boolean[][][] thetaSamples = new boolean[nbOfSamples][nbOfBasicFunctions][nbOfBits];

        System.out.println(this);

        for (int nbOfIt = 0; nbOfIt < 50; nbOfIt++)
        {
            startTime = new Date().getTime();

            System.out.println((nbOfIt + 1) + "th iteration starting...");
            System.out.println(this);

            // Generate samples
            System.out.println("Generating samples...");
            boolean isValidSample = false;
            for (int i = 0; i < nbOfSamples; i++)
            {
                for (int j = 0; j < nbOfBasicFunctions; j++)
                {
                    for (int k = 0; k < 7; k++)
                    {
                        isValidSample = false;
                        while (!isValidSample)
                        {
                            epsilonSamples[i][j][0][k] = MathTools
                                .nextGaussian(centersMeans[j][k],
                                    centersStdDevs[j][k]);

                            isValidSample = isValidMean(
                                epsilonSamples[i][j][0][k], k);

                        }

                        isValidSample = false;
                        while (!isValidSample)
                        {
                            epsilonSamples[i][j][1][k] = MathTools
                                .nextGaussian(radiiMeans[j][k],
                                    radiiStdDevs[j][k]);

                            if (epsilonSamples[i][j][1][k] > 0.0001f)
                            {
                                isValidSample = true;
                            }
                        }

                    }
                    int actionNb = nbOfDiscreteActions;
                    while (actionNb >= nbOfDiscreteActions)
                    {
                        for (int k = 0; k < nbOfBits; k++)
                        {
                            thetaSamples[i][j][k] = MathTools
                                .nextBernoulli(bernoulliMeans[j][k]);
                        }
                        actionNb = MathTools.toDecimal(thetaSamples[i][j]);
                    }
                }
            }

            // Compute score for each sample
            System.out.println("Computing samples scores...");
            TreeMap<Float, ArrayList<Integer>> samplesScore = new TreeMap<Float, ArrayList<Integer>>();
            float score;
            for (int i = 0; i < nbOfSamples; i++)
            {
                for (int j = 0; j < nbOfDiscreteActions; j++)
                {
                    actionToBasicFunctions.get(j).clear();
                }

                int associatedAction;
                for (int j = 0; j < nbOfBasicFunctions; j++)
                {
                    basicFunctions[j].setCenters(epsilonSamples[i][j][0]);
                    basicFunctions[j].setRadii(epsilonSamples[i][j][1]);

                    associatedAction = MathTools.toDecimal(thetaSamples[i][j]);
                    actionToBasicFunctions.get(associatedAction).add(
                        basicFunctions[j]);
                }

                score = 0.0f;
                for (State s : initialStates)
                {
                    score += MarkovDecisionProcess
                        .trajectoryReward(s, this, 30)
                            / initialStates.size();
                }

                ArrayList<Integer> l = samplesScore.get(score);
                if (l == null)
                {
                    l = new ArrayList<Integer>();
                }
                l.add(i);
                samplesScore.put(score, l);

                float percentageDone = 100.0f * (float) i / (float) nbOfSamples;
                if (i % Math.round((float) nbOfSamples / 100f * 10f) == 0)
                {
                    System.out.print(Math.round(percentageDone) + "% ");
                }
            }
            System.out.println();

            // Get best samples
            int nbOfGoodSamples = (int) (Math.ceil(percentageOfGoodSamples
                    * nbOfSamples));

            int[] goodSamplesIndexes = new int[nbOfGoodSamples];
            ArrayList<Integer> l = samplesScore.pollLastEntry().getValue();
            for (int i = 0; i < nbOfGoodSamples; i++)
            {
                if (l.isEmpty())
                {
                    l = samplesScore.pollLastEntry().getValue();
                }
                goodSamplesIndexes[i] = l.remove(l.size() - 1);
            }

            // Compute the new means and standard deviations for the parameters
            System.out.println("Updating means and standard deviations...");
            float goodSamplesCenters[] = new float[goodSamplesIndexes.length];
            float goodSamplesRadii[] = new float[goodSamplesIndexes.length];
            boolean goodSamplesBernoulli[] = new boolean[goodSamplesIndexes.length];
            for (int j = 0; j < nbOfBasicFunctions; j++)
            {
                for (int k = 0; k < 7; k++)
                {
                    for (int i = 0; i < goodSamplesIndexes.length; i++)
                    {
                        goodSamplesCenters[i] = epsilonSamples[goodSamplesIndexes[i]][j][0][k];
                        goodSamplesRadii[i] = epsilonSamples[goodSamplesIndexes[i]][j][1][k];
                    }
                    centersMeans[j][k] = MathTools.mean(goodSamplesCenters);
                    radiiMeans[j][k] = MathTools.mean(goodSamplesRadii);
                    centersStdDevs[j][k] = MathTools.stdDev(goodSamplesCenters,
                        centersMeans[j][k]);
                    radiiStdDevs[j][k] = MathTools.stdDev(goodSamplesRadii,
                        radiiMeans[j][k]);
                }

                for (int k = 0; k < nbOfBits; k++)
                {
                    for (int i = 0; i < goodSamplesIndexes.length; i++)
                    {
                        goodSamplesBernoulli[i] = thetaSamples[goodSamplesIndexes[i]][j][k];
                    }
                    bernoulliMeans[j][k] = MathTools.mean(goodSamplesBernoulli);
                }
            }

            // Update basic functions parameters using the best samples
            for (int i = 0; i < nbOfBasicFunctions; i++)
            {
                basicFunctions[i].setCenters(centersMeans[i]);
                basicFunctions[i].setRadii(radiiMeans[i]);
            }
            
            // Compute performance
            System.out.println("Computing performance scores...");
            computePerformance(initialStates,true);
            computePerformance(performanceTestStates,false);

            // Update number of iterations etc
            totalNbOfIterations++;
            finishTime = new Date().getTime();
            totalComputationTime += (finishTime - startTime);

            save();
        }
    }

    /**
     * Checks whether the mean sample belongs to the right interval 
     * according to the state variable number (1,...,7).
     * 
     * @param sample
     * @param stateVariableNb
     * @return
     */
    private boolean isValidMean(float sample, int stateVariableNb)
    {
        boolean isValidSample = false;

        switch (stateVariableNb)
        {
        case 0:
            if (sample >= 0.0f && sample <= SoccerParams.BALL_SPEED_MAX)
            {
                isValidSample = true;
            }
            break;
        case 1:
            if (sample >= -180.0f && sample <= 180.0f)
            {
                isValidSample = true;
            }
            break;
        case 2:
            if (sample >= 0.0f && sample <= SoccerParams.PLAYER_SPEED_MAX)
            {
                isValidSample = true;
            }
            break;
        case 3:
            if (sample >= -180.0f && sample <= 180.0f)
            {
                isValidSample = true;
            }
            break;
        case 4:
            if (sample >= -180.0f && sample <= 180.0f)
            {
                isValidSample = true;
            }
            break;
        case 5:
            if (sample >= 0.0f && sample <= 125.0f)
            {
                isValidSample = true;
            }
            break;
        case 6:
            if (sample >= -180.0f && sample <= 180.0f)
            {
                isValidSample = true;
            }
            break;
        default:
            break;

        }

        return isValidSample;
    }

    /**
     * 
     */
    private void generateStates()
    {
        State s;
        for (float i = 0; i < 3.0f; i += 3.0f)
        {
            for (float j = -180.0f; j < 180.0f; j += 120.0f)
            {
                for (float k = 0; k < 1.05f; k += 1.05f)
                {
                    for (float l = -180.0f; l < 180.0f; l += 120.0f)
                    {
                        for (float m = -180.0f; m < 180.0f; m += 120.0f)
                        {
                            for (float n = 0; n < 125.0f; n += 25.0f)
                            {
                                for (float o = -180.0f; o < 180.0f; o += 120.0f)
                                {
                                    s = new State(i, j, k, l, m, n, o);
                                    initialStates.add(s);
                                }

                            }

                        }

                    }

                }

            }

        }
        
        for (float i = 1.5f; i < 3.0f; i += 3.0f)
        {
            for (float j = -155.0f; j < 180.0f; j += 105.0f)
            {
                for (float k = 0.9f; k < 1.05f; k += 1.05f)
                {
                    for (float l = -164.0f; l < 180.0f; l += 130.0f)
                    {
                        for (float m = -145.0f; m < 180.0f; m += 96.0f)
                        {
                            for (float n = 0.9f; n < 125.0f; n += 12.0f)
                            {
                                for (float o = -170.0f; o < 180.0f; o += 60.0f)
                                {
                                    s = new State(i, j, k, l, m, n, o);
                                    performanceTestStates.add(s);
                                }

                            }

                        }

                    }

                }

            }

        }
        

    }

    public void computePerformance(LinkedList<State> states, boolean isInitStatesList)
    {
        int nbOfBadStates = 0;
        float totalScore = 0f;

        float score;
        for (State s : states)
        {
            score = MarkovDecisionProcess.trajectoryReward(s, this, 100);
            if (score < 0f)
            {
                nbOfBadStates++;
            }
            else
            {
                totalScore += score;
            }
        }

        float averageScore = totalScore
                / (float) (states.size() - nbOfBadStates);
        
        if (isInitStatesList)
        {
            this.averageScores[0] = averageScore;
            this.nbOfBadStates[0] = nbOfBadStates;            
        }
        else
        {
            this.averageScores[1] = averageScore;
            this.nbOfBadStates[1] = nbOfBadStates;                        
        }
    }

    /**
     * @param filename
     */
    public void save(String filename)
    {
        try
        {
            FileOutputStream fos = new FileOutputStream(filename);
            GZIPOutputStream gzos = new GZIPOutputStream(fos);
            ObjectOutputStream out = new ObjectOutputStream(gzos);
            out.writeObject(this);
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    /**
     * 
     */
    public void save()
    {
        String filename = String.valueOf(nbOfBasicFunctions) + "_"
                + String.valueOf(nbOfSamples) + "_"
                + String.valueOf(totalNbOfIterations) + ".zip";

        save(filename);
    }

    /**
     * @param filename
     * @return
     */
    public static synchronized DirectPolicySearch load(String filename)
    {
        DirectPolicySearch dps = null;
        try
        {
            FileInputStream fis = new FileInputStream(filename);
            GZIPInputStream gzis = new GZIPInputStream(fis);
            ObjectInputStream in = new ObjectInputStream(gzis);
            dps = (DirectPolicySearch) in.readObject();
            in.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        System.out.println(dps);

        return dps;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        String str = "";

        str += "Nb of samples: " + nbOfSamples + "\n";

        str += "Nb of basic functions: " + nbOfBasicFunctions + "\n";
        str += "Nb of discrete actions: " + nbOfDiscreteActions + "\n";
        str += "Nb of initial states: " + initialStates.size() + "\n";
        str += "Nb of performance test states: " + performanceTestStates.size() + "\n";
        str += "Performance scores: " + averageScores[0] + " ; " + averageScores[1] + "\n";
        str += "Nb of bad states: " + nbOfBadStates[0] + " ; " + nbOfBadStates[1] + "\n";
        str += "Total number of iterations completed so far: "
                + totalNbOfIterations + "\n";
        str += "Total computation time so far (min): "
                + ((float) (totalComputationTime) / 1000f / 60f) + "\n";

        return str;
    }

}
