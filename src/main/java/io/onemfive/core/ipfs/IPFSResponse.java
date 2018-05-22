package io.onemfive.core.ipfs;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class IPFSResponse {
    public MerkleNode merkleNode;
    public List<MerkleNode> merkleNodes;
    public byte[] resultBytes;
    public InputStream inputStream;
    public Map resultMap;
    public String resultString;
    public List<Multihash> multihashs;
    public Map<Multihash, Object> pins;
    public Object resultObject;
    public Map<String, Object> stats;

}
