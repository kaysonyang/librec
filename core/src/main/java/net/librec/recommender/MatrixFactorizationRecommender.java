package net.librec.recommender;

import net.librec.common.LibrecException;
import net.librec.math.structure.DenseMatrix;

/**
 * Matrix Factorization Recommender
 * Methods with user factors and item factors: such as SVD(Singular Value Decomposition)
 * <p>
 * Created by Keqiang Wang
 */
public abstract class MatrixFactorizationRecommender extends AbstractRecommender {
    /**
     * learn rate, maximum learning rate
     */
    protected float learnRate, maxLearnRate;

    /**
     * user latent factors
     */
    protected DenseMatrix userFactors;

    /**
     * item latent factors
     */
    protected DenseMatrix itemFactors;

    /**
     * the number of latent factors;
     */
    protected int numFactors;

    /**
     * the number of iterations
     */
    protected int numIterations;

    /**
     * init mean
     */
    protected float initMean;

    /**
     * init standard deviation
     */
    protected float initStd;

    /**
     * user regularization
     */
    protected float regUser;

    /**
     * item regularization
     */
    protected float regItem;

    /**
     * global mean
     */
    protected double globalMean;

    /**
     * setup
     * init member method
     *
     * @throws LibrecException if error occurs during setting up
     */
    protected void setup() throws LibrecException {
        super.setup();
        numIterations = conf.getInt("rec.iterator.maximum");
        learnRate = conf.getFloat("rec.iterator.learnrate", 0.01f);
        maxLearnRate = conf.getFloat("rec.iterator.learnrate.maximum", 1000.0f);

        regUser = conf.getFloat("rec.user.regularization", 0.01f);
        regItem = conf.getFloat("rec.item.regularization", 0.01f);

        numFactors = conf.getInt("rec.factor.number", 10);
        isBoldDriver = conf.getBoolean("rec.learnrate.bolddriver", false);
        decay = conf.getFloat("rec.learnrate.decay", 1.0f);

        userFactors = new DenseMatrix(numUsers, numFactors);
        itemFactors = new DenseMatrix(numItems, numFactors);
        globalMean = trainMatrix.mean();

        initMean = 0.0f;
        initStd = 0.1f;

        // initialize factors
        userFactors.init(initMean, initStd);
        itemFactors.init(initMean, initStd);
    }

    /**
     * predict a specific rating for user userIdx on item itemIdx.
     *
     * @param userIdx user index
     * @param itemIdx item index
     * @return predictive rating for user userIdx on item itemIdx with bound
     * @throws LibrecException if error occurs during predicting
     */
    protected double predict(int userIdx, int itemIdx) throws LibrecException {
        return DenseMatrix.rowMult(userFactors, userIdx, itemFactors, itemIdx);
    }

    /**
     * Post each iteration, we do things:
     * <ol>
     * <li>print debug information</li>
     * <li>check if converged</li>
     * <li>if not, adjust learning rate</li>
     * </ol>
     *
     * @param iter current iteration
     * @return boolean: true if it is converged; false otherwise
     */
    protected boolean isConverged(int iter) throws LibrecException{
        float delta_loss = (float) (lastLoss - loss);

        // print out debug info
        if (verbose) {
            String recName = getClass().getSimpleName().toString();
            String info = recName + " iter " + iter + ": loss = " + loss + ", delta_loss = " + delta_loss;
            LOG.info(info);
        }

        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
//            LOG.error("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
            throw new LibrecException("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
        }

        // check if converged
        boolean converged = Math.abs(loss) < 1e-5;
        lastLoss = loss;

        return converged;
    }

    /**
     * Update current learning rate after each epoch <br>
     * <ol>
     * <li>bold driver: Gemulla et al., Large-scale matrix factorization with distributed stochastic gradient descent,
     * KDD 2011.</li>
     * <li>constant decay: Niu et al, Hogwild!: A lock-free approach to parallelizing stochastic gradient descent, NIPS
     * 2011.</li>
     * <li>Leon Bottou, Stochastic Gradient Descent Tricks</li>
     * <li>more ways to adapt learning rate can refer to: http://www.willamette.edu/~gorr/classes/cs449/momrate.html</li>
     * </ol>
     * @param iter the current iteration
     */
    protected void updateLRate(int iter) {
        if (learnRate < 0.0) {
            return;
        }

        if (isBoldDriver && iter > 1) {
            learnRate = Math.abs(lastLoss) > Math.abs(loss) ? learnRate * 1.05f : learnRate * 0.5f;
        } else if (decay > 0 && decay < 1) {
            learnRate *= decay;
        }

        // limit to max-learn-rate after update
        if (maxLearnRate > 0 && learnRate > maxLearnRate) {
            learnRate = maxLearnRate;
        }
    }
}