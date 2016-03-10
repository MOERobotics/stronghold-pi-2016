#ifndef NODE_NAME
#error No NODE_NAME set
#endif
#ifndef NODE_TYPE
#define NODE_TYPE NODE_NAME ## _t
#endif
struct NODE_NAME;
typedef struct NODE_NAME {
	struct NODE_NAME* next;
	struct NODE_NAME* prev;
	NODE_VALUE value;
} NODE_TYPE;

#define FN_NAME(name) NODE_NAME ## _ ## name

size_t FN_NAME(toArray) (NODE_TYPE* node, NODE_VALUE** result) {
	NODE_TYPE* start_node = node;
	NODE_TYPE* end_node = node;
	size_t num_nodes = FN_NAME(getLast) (&end_node, 65535);
	*result = calloc(num_nodes, sizeof(point_t));
	point_t** result_ptr = result;
	while (*(result_ptr++) = (node = node->next) && node != start_node && node != end_node);
	return num_nodes;
}

void FN_NAME(remove) (NODE_TYPE* current) {
	if (current == NULL)
		return;
	NODE_TYPE* prev = current->prev;
	NODE_TYPE* next = current->next;
	if (prev != NULL && next != NULL) {
		prev->next = next;
		next->prev = prev;
	} else if (prev != NULL) {
		prev->next = NULL;
	} else if (next != NULL) {
		next->prev = NULL;
	}
	free(current);
}

NODE_TYPE* FN_NAME(skip) (NODE_TYPE* node, size_t skip) {
	while (skip-- && node->next)
		node = node->next;
	return node;
}

NODE_TYPE* FN_NAME(skipBackwards) (NODE_TYPE* node, size_t skip) {
	while (skip-- && node->prev)
		node = node->prev;
	return node;
}

size_t FN_NAME(getLast) (NODE_TYPE** node, size_t maxSearch) {
	NODE_TYPE* start_node = *node;
	if (start_node == NULL)
		return 0;
	
	NODE_TYPE* next_node;
	size_t i = 0;
	while ((next_node = (*node)->next) != NULL && next_node != start_node) {
		*node = next_node;
		if (i++ > maxSearch)
			return i;
	}
	return i;
}
#undef NODE_NAME
#undef NODE_TYPE
#undef NODE_VALUE
