package org.openml.learningcurves.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openml.learningcurves.data.DataLoader;
import org.openml.learningcurves.data.Evaluation;
import org.openml.learningcurves.data.PairedRanking;
import org.openml.learningcurves.utils.OrderedMap;

public class BestAlgorithmGlobalRanking implements CurvesExperimentFull {
	
	protected static final String EXPERIMENT_NAME = "Best algorithm (Global Ranking)";

	protected final DataLoader dl;
	
	protected final Map<Integer, Map<Integer, Map<Integer, Evaluation>>> taskOriented;
	protected final Map<Integer, Map<Integer, Map<Integer, Evaluation>>> setupOriented;
	
	protected final Map<Integer, List<Double>> losscurves;
	protected int tasksCorrect = 0;
	protected int tasksTotal = 0;
	
	protected final Map<Integer, OrderedMap> globalRankings;
	
	public BestAlgorithmGlobalRanking( DataLoader dl ) {
		this.dl = dl;
		
		// book keeping
		this.taskOriented = dl.getTaskOriented();
		this.setupOriented = dl.getSetupOriented();
		
		// scores
		losscurves = new HashMap<Integer, List<Double>>();
		
		// book keeping
		globalRankings = new TreeMap<Integer, OrderedMap>();
	}
	
	public void allTasks() {
		for( Integer task_id : taskOriented.keySet() ) {
			singleTask( task_id );
		}
	}
	
	public void singleTask( int task_id ) {
		
		// setup, rank
		Map<Integer, Integer> accumulatedRank = new HashMap<>();
		
		// first calculate accumulated ranking
		for( int otherTask : taskOriented.keySet() ) {
			if( otherTask == task_id ) { continue; }
			
			OrderedMap orderedmap = new OrderedMap();

			int sampleNr = dl.taskSamples( otherTask ) - 1;
			for( int setup_id : setupOriented.keySet() ) {
				orderedmap.put( setup_id, taskOriented.get( otherTask ).get( setup_id ).get( sampleNr ).getAccuracy() );
			}
			
			for( int setup_id : setupOriented.keySet() ) {
				int currentRank = orderedmap.getRankByKey( setup_id );
				
				if( accumulatedRank.containsKey( setup_id ) == false ) {
					accumulatedRank.put( setup_id, orderedmap.size() - currentRank );
				} else {
					accumulatedRank.put( setup_id, accumulatedRank.get( setup_id ) + orderedmap.size() - currentRank );
				}
			}
		}
		
		OrderedMap globalRanking = new OrderedMap();
		for( Integer setup : accumulatedRank.keySet() ) {
			globalRanking.put( setup, accumulatedRank.get( setup ) / ((taskOriented.keySet().size() - 1) * 1.0) );
		}
		
		globalRankings.put( task_id, globalRanking );
		
		
		PairedRanking pr = new PairedRanking( dl.getTaskSetupFoldResults().get( task_id ) );
		Map<Integer, List<Integer>> partialOrdering = pr.partialOrdering();
		OrderedMap accuracyOrdering = pr.accuracyOrdering();
		
		losscurves.put( task_id, new ArrayList<Double>() );
		
		int currentBest = globalRanking.getKeyByRank( 0 );
		double currentLoss = accuracyOrdering.getValueByRank( accuracyOrdering.size() - 1 ) - accuracyOrdering.getValueByKey( currentBest );
		losscurves.get( task_id ).add( currentLoss );

		/*
		System.out.println( "Task " + task_id );
		System.out.println( "global ranking: " + globalRanking );
		System.out.println( "predicted: " + currentBest );
		System.out.println( "ground truth: " + pr.accuracyOrdering() );
		*/
		for( int i = 1; currentLoss > 0 && globalRanking.size() > 0; ++i ) {
			int currentAttempt = globalRanking.getKeyByRank( i );
			
			double updatedLoss = accuracyOrdering.getValueByRank( accuracyOrdering.size() - 1 ) - accuracyOrdering.getValueByKey( currentAttempt );
			if( updatedLoss < currentLoss ) {
				currentLoss = updatedLoss;
			}
			
			losscurves.get( task_id ).add( currentLoss );
		}
		
		tasksTotal += 1;
		if( partialOrdering.get( 0 ).contains( currentBest ) ) {
			tasksCorrect += 1;
		}
		
	}
	
	public String result() {
		StringBuilder sb = new StringBuilder();
		sb.append( EXPERIMENT_NAME + "\n" );
		sb.append( "Total: " + tasksTotal + "; correct: " + tasksCorrect );
		return sb.toString();
	}
	
	public List<Double> lossCurve() {
		List<Double> averageLossCurve = new ArrayList<Double>();
		
		for( Integer task_id : losscurves.keySet() ) {
			for( int i = 0; i < losscurves.get(task_id).size(); ++i  ) {
				if( averageLossCurve.size() <= i ) {
					averageLossCurve.add( losscurves.get(task_id).get(i) );
				} else {
					averageLossCurve.set( i, averageLossCurve.get(i) + losscurves.get(task_id).get(i) );
				}
			}
		}
		
		for( int i = 0; i < averageLossCurve.size(); ++i ) {
			averageLossCurve.set( i, averageLossCurve.get(i) / losscurves.keySet().size() );
		}
		
		return averageLossCurve;
	}
	
	public String globalRankingCsv() {
		StringBuilder sb = new StringBuilder();
		
		List<Integer> tasks = new ArrayList<>(taskOriented.keySet());
		List<Integer> setups = new ArrayList<>(setupOriented.keySet());
		Collections.sort(tasks);
		Collections.sort(setups);
		
		// write header
		for( int iTasks = 0; iTasks < tasks.size(); ++iTasks ) {
			sb.append( "," + tasks.get(iTasks) );
		}
		sb.append("\n");
		
		// now the content
		for( int iSetups = 0; iSetups < setups.size(); ++iSetups ) {
			sb.append(setups.get(iSetups));
			for( int iTasks = 0; iTasks < tasks.size(); ++iTasks ) {
				Double meanRank = globalRankings.get(tasks.get(iTasks)).getValueByKey(setups.get(iSetups) );
				
				sb.append( "," + meanRank );
			}
			sb.append("\n");
		}
		
		return sb.toString();
	}
}
