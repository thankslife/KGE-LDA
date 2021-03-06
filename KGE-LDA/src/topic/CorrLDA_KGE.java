package topic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.Common;

public class CorrLDA_KGE {

	int[][] documents;

	int[][] entities;

	int V;

	int E;

	int K;

	double alpha;

	double beta;

	int[][] z;

	int[][] z_bar;

	int[][] nw;

	int[][] ne;

	int[][] nd;

	int[] nwsum;

	int[] nesum;

	int[] ndsum;

	int iterations;

	Map<Integer, double[]> entity_vectors;

	double[] kappa;

	double C_0 = 1;

	double m_0 = 2;

	double sigma_0 = 0.5;

	double[] miu_0;

	double[][] k_vector;

	int sample_size = 100;

	int dimension;

	double c_temp;

	double[] c_temp_kappa;

	double[] temp;

	public CorrLDA_KGE(int[][] documents, int[][] entities, int V, int E, Map<Integer, double[]> entity_vectors,
			int dimension) {

		this.documents = documents;
		this.entities = entities;
		this.V = V;
		this.E = E;

		this.entity_vectors = entity_vectors;
		this.dimension = dimension;

	}

	public void initialState() {

		int D = documents.length;

		nd = new int[D][K];
		ndsum = new int[D];

		nw = new int[V][K];
		nwsum = new int[K];

		ne = new int[E][K];
		nesum = new int[K];

		z = new int[D][];
		z_bar = new int[D][];

		k_vector = new double[K][dimension];
		kappa = Common.randoms_kappa_log_normal(m_0, sigma_0, K);

		c_temp = Common.C(C_0, dimension);

		temp = Common.vector_times(miu_0, C_0);

		c_temp_kappa = new double[K];

		for (int d = 0; d < D; d++) {

			// words
			int Nd = documents[d].length;
			z[d] = new int[Nd];

			for (int n = 0; n < Nd; n++) {

				int topic = (int) (Math.random() * K);
				z[d][n] = topic;

				updateCount(d, topic, documents[d][n], +1);
			}

			// entities
			int Ed = entities[d].length;
			z_bar[d] = new int[Ed];

			for (int m = 0; m < Ed; m++) {

				int topic = (int) (Math.random() * K);
				z_bar[d][m] = topic;

				updateEntityCount(d, topic, entities[d][m], +1);
			}

		}

	}

	public void markovChain(int K, double alpha, double beta, double[] miu_0, double C_0, double m_0, double sigma_0,
			int iterations) {

		this.K = K;
		this.alpha = alpha;
		this.beta = beta;

		this.iterations = iterations;

		this.miu_0 = miu_0;

		this.C_0 = C_0;

		this.m_0 = m_0;

		this.sigma_0 = sigma_0;

		initialState();

		for (int i = 0; i < this.iterations; i++) {

			System.out.println("iteration : " + i);
			gibbs();
		}
	}

	public void gibbs() {

		for (int d = 0; d < documents.length; d++) {

			Set<Integer> topic_set = new HashSet<>();

			// words
			for (int n = 0; n < z[d].length; n++) {

				int topic = sampleFullConditional(d, n);
				z[d][n] = topic;

				topic_set.add(topic);

			}

			List<Integer> topic_list = new ArrayList<>(topic_set);

			// entities
			for (int m = 0; m < z_bar[d].length; m++) {

				int topic = sampleFullConditionalEntity(d, m, topic_list);
				z_bar[d][m] = topic;

			}
		}

		for (int k = 0; k < K; k++) {

			// sample kappa_k

			double[] random_numbers = Common.randoms_kappa_log_normal(m_0, sigma_0, sample_size);

			double[] p_1 = new double[random_numbers.length];

			for (int p_index = 0; p_index < p_1.length; p_index++) {

				double[] denominator = Common.vector_times(k_vector[k], random_numbers[p_index]);

				denominator = Common.vector_add(denominator, temp, +1);

				p_1[p_index] = Common.probability(m_0, sigma_0, random_numbers[p_index]) * c_temp
						* Math.pow(Common.C(random_numbers[p_index], dimension), nesum[k])
						/ Common.C(Common.l2norm(denominator), dimension);

			}

			int index = sample(p_1);

			kappa[k] = random_numbers[index];

			c_temp_kappa[k] = Common.C(kappa[k], dimension);

		}
	}

	int sampleFullConditional(int d, int n) {

		int topic = z[d][n];

		updateCount(d, topic, documents[d][n], -1);

		double[] p = new double[K];

		for (int k = 0; k < K; k++) {

			p[k] = (nd[d][k] + alpha) / (ndsum[d] + K * alpha) * (nw[documents[d][n]][k] + beta)
					/ (nwsum[k] + V * beta);
		}

		topic = sample(p);

		updateCount(d, topic, documents[d][n], +1);

		return topic;

	}

	int sampleFullConditional(int d, int n, List<Integer> topic_list) {

		int topic = z[d][n];

		updateCount(d, topic, documents[d][n], -1);

		double[] p = new double[topic_list.size()];

		for (int k = 0; k < p.length; k++) {

			p[k] = ((double) nd[d][topic_list.get(k)]) / (ndsum[d]) * (nw[documents[d][n]][topic_list.get(k)] + beta)
					/ (nwsum[topic_list.get(k)] + V * beta);

		}

		int index = sample(p);

		topic = topic_list.get(index);

		updateCount(d, topic, documents[d][n], +1);

		return topic;

	}

	/**
	 * 只能从当前文档词的主题分配中选择
	 * 
	 * @param d
	 * @param m
	 * @param topic_list
	 * @return
	 */
	int sampleFullConditionalEntity(int d, int m, List<Integer> topic_list) {

		int topic = z_bar[d][m];

		double[] vector_before = k_vector[topic];

		updateEntityCount(d, topic, entities[d][m], -1);

		double[] vector_after = k_vector[topic];

		double[] p = new double[topic_list.size()];

		for (int k = 0; k < p.length; k++) {

			// sample z_bar
			if (topic != topic_list.get(k)) {
				p[k] = ((double) nd[d][topic_list.get(k)]) / (ndsum[d]) * c_temp_kappa[topic_list.get(k)];
			} else {

				double[] numerator = Common.vector_times(vector_after, kappa[topic_list.get(k)]);

				numerator = Common.vector_add(numerator, temp, +1);

				double[] denominator = Common.vector_times(vector_before, kappa[topic_list.get(k)]);

				denominator = Common.vector_add(denominator, temp, +1);

				p[k] = ((double) nd[d][topic_list.get(k)]) / (ndsum[d]) * c_temp_kappa[topic_list.get(k)]
						* Common.C(Common.l2norm(numerator), dimension)
						/ Common.C(Common.l2norm(denominator), dimension);

			}

		}
		int index = sample(p);

		topic = topic_list.get(index);

		updateEntityCount(d, topic, entities[d][m], +1);

		return topic;

	}

	/**
	 * 根据吉布斯采样公式计算实体主题分配
	 * 
	 * @param d
	 * @param m
	 * @return
	 */
	int sampleFullConditionalEntity(int d, int m) {

		int topic = z_bar[d][m];

		double[] vector_before = k_vector[topic];

		updateEntityCount(d, topic, entities[d][m], -1);

		double[] vector_after = k_vector[topic];

		double[] p = new double[K];

		for (int k = 0; k < K; k++) {

			// sample z_bar
			if (topic != k) {
				p[k] = (nd[d][k] + alpha) / (ndsum[d] + K * alpha) * c_temp_kappa[k];
			} else {

				double[] numerator = Common.vector_times(vector_after, kappa[k]);

				numerator = Common.vector_add(numerator, temp, +1);

				double[] denominator = Common.vector_times(vector_before, kappa[k]);

				denominator = Common.vector_add(denominator, temp, +1);

				p[k] = (nd[d][k] + alpha) / (ndsum[d] + K * alpha) * c_temp_kappa[k]
						* Common.C(Common.l2norm(numerator), dimension)
						/ Common.C(Common.l2norm(denominator), dimension);

			}

		}
		topic = sample(p);

		updateEntityCount(d, topic, entities[d][m], +1);

		return topic;

	}

	int sample(double[] p) {

		int topic = 0;
		for (int k = 1; k < p.length; k++) {
			p[k] += p[k - 1];
		}
		double u = Math.random() * p[p.length - 1];
		for (int t = 0; t < p.length; t++) {
			if (u < p[t]) {
				topic = t;
				break;
			}
		}
		return topic;
	}

	void updateCount(int d, int topic, int word, int flag) {

		nd[d][topic] += flag;
		ndsum[d] += flag;
		nw[word][topic] += flag;
		nwsum[topic] += flag;
	}

	void updateEntityCount(int d, int topic, int entity, int flag) {

		ne[entity][topic] += flag;
		nesum[topic] += flag;

		nd[d][topic] += flag;
		ndsum[d] += flag;

		k_vector[topic] = Common.vector_add(k_vector[topic], entity_vectors.get(entity), flag);
	}

	public double[][] estimateTheta() {
		double[][] theta = new double[documents.length][K];
		for (int d = 0; d < documents.length; d++) {
			for (int k = 0; k < K; k++) {
				theta[d][k] = (nd[d][k] + alpha) / (ndsum[d] + K * alpha);
			}
		}
		return theta;
	}

	public double[][] estimatePhi() {
		double[][] phi = new double[K][V];
		for (int k = 0; k < K; k++) {
			for (int w = 0; w < V; w++) {
				phi[k][w] = (nw[w][k] + beta) / (nwsum[k] + V * beta);
			}
		}
		return phi;
	}

}
